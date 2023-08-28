(ns nextjournal.clerk.webserver
  (:require [babashka.fs :as fs]
            [clojure.edn :as edn]
            [clojure.pprint :as pprint]
            [clojure.set :as set]
            [clojure.string :as str]
            [editscript.core :as editscript]
            [nextjournal.clerk.config :as config]
            [nextjournal.clerk.view :as view]
            [nextjournal.clerk.viewer :as v]
            [org.httpkit.server :as httpkit])
  (:import (java.nio.file Files)))

(defn help-hiccup []
  [:p "Call " [:span.code "nextjournal.clerk/show!"] " from your REPL"
   (when-let [watch-paths (seq (:paths @@(resolve 'nextjournal.clerk/!watcher)))]
     (into [:<> " or save a file in "]
           (interpose " or " (map #(vector :span.code %) watch-paths))))
   " to make your notebook appear…"])

(defn help-doc []
  {:blocks [{:type :code
             :visibility {:code :hide, :result :show}
             :result {:nextjournal/value (v/html (help-hiccup))}}]})

(defonce !clients (atom #{}))
(defonce !doc (atom nil))
(defonce !last-sender-ch (atom nil))

#_(view/doc->viewer @!doc)
#_(reset! !doc nil)

(def ^:dynamic *sender-ch* nil)

(defn send! [ch msg]
  (httpkit/send! ch (v/->edn msg)))

(defn broadcast! [msg]
  (doseq [ch @!clients]
    (when (not= @!last-sender-ch *sender-ch*)
      (send! ch {:type :patch-state! :patch []
                 :effects [(v/->ViewerEval (list 'nextjournal.clerk.render/set-reset-sync-atoms! (not= *sender-ch* ch)))]}))
    (httpkit/send! ch (v/->edn msg)))
  (reset! !last-sender-ch *sender-ch*))

#_(broadcast! [{:random (rand-int 10000) :range (range 100)}])

(defn ^:private percent-decode [s]
  (java.net.URLDecoder/decode s java.nio.charset.StandardCharsets/UTF_8))

(defn ^:private decode-param-pair [param]
  (let [[k v] (str/split param #"=")]
    [(keyword (percent-decode k)) (if v (percent-decode (str/replace v #"\+" " ")) "")]))

(defn ^:private query-string->map [s]
  (if (str/blank? s) {} (into {} (map decode-param-pair) (str/split s #"&"))))

(defn get-fetch-opts [query-string]
  (-> query-string
      query-string->map
      (v/update-if :n #(Integer/parseInt %))
      (v/update-if :offset #(Integer/parseInt %))
      (v/update-if :path #(edn/read-string %))))

#_(get-fetch-opts "")
#_(get-fetch-opts "foo=bar&n=42&start=20")

(defn blob->presented [presented-doc]
  ;; TODO: store on doc?
  (into {}
        (comp (filter #(and (map? %) (v/get-safe % :nextjournal/blob-id) (v/get-safe % :nextjournal/presented)))
              (map (juxt :nextjournal/blob-id :nextjournal/presented)))
        (tree-seq coll? seq
                  (:nextjournal/value presented-doc))))

#_(blob->presented (meta @!doc))

(defn serve-blob [{:as doc :keys [ns]} {:keys [blob-id fetch-opts]}]
  (when-not ns
    (throw (ex-info "namespace must be set" {:doc doc})))
  (if-some [root-desc (get (blob->presented (meta doc)) blob-id)]
    (let [{:keys [present-elision-fn]} (meta root-desc)
          desc (present-elision-fn fetch-opts)]
      (if (contains? desc :nextjournal/content-type)
        {:body (v/->value desc)
         :content-type (:nextjournal/content-type desc)}
        {:body (v/->edn (view/+name->viewer desc))}))
    {:status 404}))

(defn extract-blob-opts [{:as _req :keys [uri query-string]}]
  {:blob-id (str/replace uri "/_blob/" "")
   :fetch-opts (get-fetch-opts query-string)})

(defn serve-file [uri path]
  (let [file (when (fs/exists? path)
               (cond-> path
                 (fs/directory? path) (fs/file "index.html")))]
    (if (fs/exists? file)
      {:status 200
       :headers (cond-> {"Content-Type" (Files/probeContentType (fs/path file))}
                  (and (= "js" (fs/extension file)) (fs/exists? (str file ".map"))) (assoc "SourceMap" (str uri ".map")))
       :body (fs/read-all-bytes file)}
      {:status 404})))

#_(serve-file "public" {:uri "/js/viewer.js"})

(defn sync-atom-changed [key atom old-state new-state]
  (eval '(nextjournal.clerk/recompute!)))

(defn maybe-cancel-send-status-future [doc]
  (when-let [scheduled-send-status-future (-> doc meta ::!send-status-future)]
    (future-cancel scheduled-send-status-future)))

(defn present+reset! [doc]
  (let [presented (view/doc->viewer doc)
        sync-vars-old (v/extract-sync-atom-vars @!doc)
        sync-vars (v/extract-sync-atom-vars doc)]
    (doseq [sync-var (set/difference sync-vars sync-vars-old)]
      (add-watch @sync-var (symbol sync-var) sync-atom-changed))
    (doseq [sync-var (set/difference sync-vars-old sync-vars)]
      (remove-watch @sync-var (symbol sync-var)))
    (maybe-cancel-send-status-future @!doc)
    (reset! !doc (with-meta doc presented))
    presented))

(defn update-doc! [{:as doc :keys [nav-path fragment skip-history?]}]
  (broadcast! (if (and (:ns @!doc) (= (:ns @!doc) (:ns doc)))
                {:type :patch-state! :patch (editscript/get-edits (editscript/diff (meta @!doc) (present+reset! doc) {:algo :quick}))}
                (cond-> {:type :set-state!
                         :doc (present+reset! doc)}
                  (and nav-path (not skip-history?))
                  (assoc :effects [(v/->ViewerEval (list 'nextjournal.clerk.render/history-push-state
                                                         (cond-> {:path nav-path} fragment (assoc :fragment fragment))))])))))

#_(update-doc! (help-doc))

(defn update-error! [ex]
  (update-doc! (assoc @!doc :error ex)))

(defn read-msg [s]
  (binding [*data-readers* v/data-readers]
    (try (read-string s)
         (catch Exception ex
           (throw (doto (ex-info (str "Clerk encountered the following error attempting to read an incoming message: "
                                      (ex-message ex))
                                 {:message s} ex)
                    update-error!))))))

#_(pr-str (read-msg "#viewer-eval (resolve 'clojure.core/inc)"))

(def ws-handlers
  {:on-open (fn [ch] (swap! !clients conj ch))
   :on-close (fn [ch _reason] (swap! !clients disj ch))
   :on-receive (fn [sender-ch edn-string]
                 (binding [*ns* (or (:ns @!doc)
                                    (create-ns 'user))]
                   (let [{:as msg :keys [type recompute?]} (read-msg edn-string)]
                     (case type
                       :eval (do (send! sender-ch (merge {:type :eval-reply :eval-id (:eval-id msg)}
                                                         (try {:reply (eval (:form msg))}
                                                              (catch Exception e
                                                                {:error (Throwable->map e)}))))
                                 (when recompute?
                                   (eval '(nextjournal.clerk/recompute!))))
                       :swap! (when-let [var (resolve (:var-name msg))]
                                (try
                                  (binding [*sender-ch* sender-ch]
                                    (apply swap! @var (eval (:args msg))))
                                  (catch Exception ex
                                    (throw (doto (ex-info (str "Clerk cannot `swap!` synced var `" (:var-name msg) "`.") msg ex) update-error!)))))))))})

#_(do
    (apply swap! nextjournal.clerk.atom/my-state (eval '[update :counter inc]))
    (eval '(nextjournal.clerk/recompute!)))

(defn ->nav-path [file-or-ns]
  (cond (or (symbol? file-or-ns) (instance? clojure.lang.Namespace file-or-ns))
        (str "'" file-or-ns)

        (string? file-or-ns)
        (when (fs/exists? file-or-ns)
          (fs/unixify (cond->> (fs/strip-ext file-or-ns)
                        (and (fs/absolute? file-or-ns)
                             (not (str/starts-with? (fs/relativize (fs/cwd) file-or-ns) "..")))
                        (fs/relativize (fs/cwd)))))

        :else (str file-or-ns)))

#_(->nav-path "notebooks/rule_30.clj")
#_(->nav-path 'nextjournal.clerk.home)

(defn find-first-existing-file [files]
  (first (filter fs/exists? files)))

(defn maybe-add-extension [nav-path]
  (if (and (string? nav-path)
           (or (str/starts-with? nav-path "'")
               (and (fs/exists? nav-path)
                    (not (fs/directory? nav-path)))))
    nav-path
    (find-first-existing-file (map #(str (fs/file nav-path) "." %) ["md" "clj" "cljc"]))))

#_(maybe-add-extension "notebooks/rule_30")
#_(maybe-add-extension "notebooks/rule_30.clj")
#_(maybe-add-extension "notebooks/markdown")
#_(maybe-add-extension "'nextjournal.clerk.home")

(defn ->file-or-ns [nav-path]
  (cond (str/blank? nav-path) (or (maybe-add-extension "index")
                                  'nextjournal.clerk.index)
        (str/starts-with? nav-path "'") (symbol (subs nav-path 1))
        (re-find #"\.(cljc?|md)$" nav-path) nav-path))

(defn show! [opts file-or-ns]
  ((resolve 'nextjournal.clerk/show!) opts file-or-ns))

(defn navigate! [{:as opts :keys [nav-path]}]
  (show! opts (->file-or-ns (maybe-add-extension nav-path))))

(defn prefetch-request? [req] (= "prefetch" (-> req :headers (get "purpose"))))

(defn serve-notebook [{:as req :keys [uri]}]
  (let [nav-path (subs uri 1)]
    (cond
      (prefetch-request? req)
      {:status 404}

      (str/blank? nav-path)
      {:status 302
       :headers {"Location" (or (:nav-path @!doc)
                                (->nav-path 'nextjournal.clerk.home))}}
      :else
      (if-let [file-or-ns (->file-or-ns (maybe-add-extension nav-path))]
        (do (try (show! {:skip-history? true} file-or-ns)
                 (catch Exception _))
            {:status 200
             :headers {"Content-Type" "text/html" "Cache-Control" "no-store"}
             :body (view/->html {:doc (view/doc->viewer @!doc)
                                 :resource->url @config/!resource->url
                                 :conn-ws? true})})
        {:status 404
         :headers {"Content-Type" "text/plain"}
         :body (format "Could not find notebook at %s." (pr-str nav-path))}))))

(defn app [{:as req :keys [uri]}]
  (if (:websocket? req)
    (httpkit/as-channel req ws-handlers)
    (try
      (case (get (re-matches #"/([^/]*).*" uri) 1)
        "_blob" (serve-blob @!doc (extract-blob-opts req))
        ("build" "js" "css") (serve-file uri (str "public" uri))
        ("_fs") (serve-file uri (str/replace uri "/_fs/" ""))
        "_ws" {:status 200 :body "upgrading..."}
        "favicon.ico" {:status 404}
        (serve-notebook req))
      (catch Throwable e
        {:status  500
         :body    (with-out-str (pprint/pprint (Throwable->map e)))}))))

#_(nextjournal.clerk/serve! {})

(defn broadcast-status! [status]
  ;; avoid editscript diff but use manual patch to just replace `:status` in doc
  (broadcast! {:type :patch-state! :patch [[[:status] :r status]]}))

(defn broadcast-status-debounced!
  "Schedules broadcasting a status update after 50 ms.

  Cancels previously scheduled broadcast, if it exists."
  [old-future status]
  (when old-future
    (future-cancel old-future))
  (future
    (Thread/sleep 50)
    (broadcast-status! status)))

(defn set-status! [status]
  (swap! !doc (fn [doc] (-> (or doc (help-doc))
                            (vary-meta assoc :status status)
                            (vary-meta update ::!send-status-future broadcast-status-debounced! status)))))

#_(clojure.java.browse/browse-url "http://localhost:7777")

;; # dynamic requirements
;; * load notebook without results
;; * allow page reload

(defonce !server (atom nil))

(defn halt! []
  (when-let [{:keys [port instance]} @!server]
    @(httpkit/server-stop! instance)
    (println (str "Webserver running on " port ", stopped."))
    (reset! !server nil)))

#_(halt!)

(defn serve! [{:keys [host port] :or {host "localhost" port 7777}}]
  (halt!)
  (try
    (reset! !server {:host host :port port :instance (httpkit/run-server #'app {:ip host :port port :legacy-return-value? false})})
    (println (format "Clerk webserver started on http://%s:%s ..." host port ))
    (catch java.net.BindException e
      (let [msg (format "Clerk webserver could not be started because port %d is not available. Stop what's running on port %d or specify a different port." port port)]
        (binding [*out* *err*]
          (println msg))
        (throw (ex-info msg {:port port} e))))))

#_(serve! {:port 7777})
#_(serve! {:port 7777 :host "0.0.0.0"})
