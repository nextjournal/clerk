(ns nextjournal.clerk.webserver
  {:clj-kondo/config '{:linters {:unresolved-namespace {:exclude [sci.core]}}}}
  (:require [babashka.fs :as fs]
            [clojure.edn :as edn]
            [clojure.java.browse :as browse]
            [clojure.java.io :as io]
            [clojure.pprint :as pprint]
            [clojure.set :as set]
            [clojure.string :as str]
            [nextjournal.clerk.config :as config]
            [nextjournal.clerk.git :as git]
            [nextjournal.clerk.paths :as paths]
            [nextjournal.clerk.utils :as u]
            [nextjournal.clerk.view :as view]
            [nextjournal.clerk.viewer :as v]
            [org.httpkit.server :as httpkit]
            [sci.nrepl.browser-server :as sci.nrepl])
  (:import (java.nio.file Files)))

(u/if-bb
 (require '[editscript.core :as-alias editscript])
 (require '[editscript.core :as editscript]))

(defonce !clients (atom #{}))
(defonce !doc (atom nil))
(defonce !last-sender-ch (atom nil))

(defonce !server (atom nil))

#_(view/doc->viewer @!doc)
#_(reset! !doc nil)

(def ^:dynamic *sender-ch* nil)

(defn server-url []
  (when-let [{:keys [host port]} @!server]
    (format "http://%s:%s" host port)))

(defn ex-server-not-running []
  (Exception. "no server running, please run `(nextjournal.clerk/serve! {})` and try again."))

(defn browse! []
  (if-let [server-url (server-url)]
    (browse/browse-url server-url)
    (throw (ex-server-not-running))))

#_(browse!)

(defn send! [ch msg]
  (httpkit/send! ch (v/->edn msg)))

(defn send-nrepl! [msg]
  (send! (first @!clients) {:type :nrepl :msg msg}))

(defn broadcast! [msg]
  (doseq [ch @!clients]
    (when (not= @!last-sender-ch *sender-ch*)
      (send! ch {:type :patch-state! :patch []
                 :effects [(v/->render-eval (list 'nextjournal.clerk.render/set-reset-sync-atoms! (not= *sender-ch* ch)))]}))
    (httpkit/send! ch (v/->edn msg)))
  (reset! !last-sender-ch *sender-ch*))

#_(broadcast! [])

(defn render-eval
  "Evaluates the given `form` in Clerk's render environment in the browser."
  [form]
  (if-let [client (first @!clients)]
    (send! client {:type :render-eval :form form})
    (if (server-url)
      (throw (ex-info (format "no client connected, please open %s in a browser and try again." (server-url)) {:clients @!clients}))
      (throw (ex-server-not-running)))))

#_(render-eval '(js/console.log :foo))

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
        {:body (v/->edn (v/process-blobs {:blob-mode :lazy-load :blob-id blob-id} desc))}))
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

(defn serve-resource [resource]
  (cond-> {:status 200
           :body (io/input-stream resource)}
    (= "js" (fs/extension (fs/file (.getFile resource))))
    (assoc :headers {"Content-Type" "text/javascript"})))

#_(serve-resource (io/resource "public/clerk_service_worker.js"))

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
  (broadcast! (u/if-not-bb-and (and (:ns @!doc) (= (:ns @!doc) (:ns doc)))
                               {:type :patch-state! :patch (editscript/get-edits (editscript/diff (meta @!doc) (present+reset! doc) {:algo :quick}))}
                               (cond-> {:type :set-state!
                                        :doc (present+reset! doc)}
                                 (and nav-path (not skip-history?))
                  (assoc :effects [(v/->render-eval (list 'nextjournal.clerk.render/history-push-state
                                                          (cond-> {:path nav-path} fragment (assoc :fragment fragment))))])))))

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

#_(pr-str (read-msg "#render-eval (resolve 'clojure.core/inc)"))

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
                                                                {:error (Throwable->map (ex-info (str "An error occured during clerk-eval: " (ex-message e))
                                                                                                 (dissoc msg :type :eval-id)
                                                                                                 e))}))))
                                 (when recompute?
                                   (eval '(nextjournal.clerk/recompute!))))
                       :sync! (if-let [var (resolve (:var-name msg))]
                                (try
                                  (binding [*sender-ch* sender-ch]
                                    (u/if-bb
                                     (throw (ex-info "Not implemented" {}))
                                     (swap! @var editscript/patch (editscript/edits->script (:patch msg)))))
                                  (catch Exception ex
                                    (throw (doto (ex-info (str "Clerk cannot update synced var `" (:var-name msg) "`.") msg ex)
                                             update-error!))))
                                (throw (doto (ex-info (str "Clerk cannot resolve synced var `" (:var-name msg) "`")
                                                      (select-keys msg [:var-name]))
                                         update-error!)))
                       :nrepl (sci.nrepl/send-response (-> (:msg msg)
                                                           (select-keys [:id :session])
                                                           (assoc :response (dissoc (:msg msg) :id :session))))))))})

#_(do
    (apply swap! nextjournal.clerk.atom/my-state (eval '[update :counter inc]))
    (eval '(nextjournal.clerk/recompute!)))

(declare present+reset!)

(defn get-build-opts
  ([] (get-build-opts @!server))
  ([{:as opts :keys [paths paths-fn index]}]
   (merge (git/read-git-attrs)
          (if (or paths paths-fn index)
            (paths/expand-paths opts)
            opts))))

#_(get-build-opts)
#_(get-build-opts {:paths ["notebooks/rule_30.clj"]})
#_(get-build-opts {:paths ["notebooks/rule_30.clj"] :index "notebooks/links.md"})
#_(get-build-opts {:paths ["notebooks/no_rule_30.clj"]})
#_(v/route-index? (get-build-opts @!server))
#_(route-index (get-build-opts @!server) "")
#_(route-index (get-build-opts {:index "notebooks/rule_30.clj"}) "")

(defn ->nav-path [file-or-ns]
  (cond (or (= 'nextjournal.clerk.index file-or-ns)
            (= (:index (get-build-opts)) file-or-ns))
        ""

        (or (symbol? file-or-ns) (instance? clojure.lang.Namespace file-or-ns))
        (str "'" file-or-ns)

        (and (string? file-or-ns) (re-matches #"^http?s://.*" file-or-ns))
        (str "/" file-or-ns)

        (string? file-or-ns)
        (paths/drop-extension (or (paths/path-in-cwd file-or-ns) file-or-ns))))

(defn find-first-existing-file [files]
  (first (filter fs/exists? files)))

(defn maybe-add-extension [nav-path]
  (if (and (string? nav-path)
           (or (str/starts-with? nav-path "'")
               (and (fs/exists? nav-path)
                    (not (fs/directory? nav-path)))))
    nav-path
    (or (find-first-existing-file (map #(str (fs/file nav-path) "." %) ["md" "clj" "cljc" "cljs"]))
        nav-path)))

#_(maybe-add-extension "notebooks/rule_30")
#_(maybe-add-extension "notebooks/rule_30.clj")
#_(maybe-add-extension "notebooks/markdown")
#_(maybe-add-extension "asdf")
#_(maybe-add-extension "'nextjournal.clerk.home")

(defn ->file-or-ns [nav-path]
  (cond (str/blank? nav-path) (or (maybe-add-extension "index")
                                  'nextjournal.clerk.index)
        (str/starts-with? nav-path "'") (symbol (subs nav-path 1))
        (re-find #"\.(clj(c|s)?|md)$" nav-path) nav-path))

(defn forbidden-path? [file-or-ns]
  (if-let [expanded-paths (:expanded-paths (get-build-opts))]
    (not (contains? (conj (set expanded-paths) 'nextjournal.clerk.index) file-or-ns))
    false))

(defn show! [opts file-or-ns]
  (when-not (forbidden-path? file-or-ns)
    ((resolve 'nextjournal.clerk/show!) opts file-or-ns)))

(defn route-index
  "A routing function"
  [{:as opts :keys [index expanded-paths]} nav-path]
  (if (str/blank? nav-path)
    (or index
        (get (set expanded-paths) (maybe-add-extension "index"))
        "'nextjournal.clerk.index")
    nav-path))

(defn maybe-route-index [opts path]
  (cond->> path
    (v/route-index? opts) (route-index opts)))

(defn navigate! [{:as opts :keys [nav-path]}]
  (let [route-opts (get-build-opts)]
    (show! (merge route-opts opts) (->file-or-ns (maybe-add-extension (maybe-route-index route-opts nav-path))))))

(defn prefetch-request? [req] (= "prefetch" (-> req :headers (get "purpose"))))

(defn serve-notebook [{:as req :keys [uri]}]
  (let [opts (get-build-opts)
        nav-path (maybe-route-index opts (subs uri 1))]
    (cond
      (prefetch-request? req)
      {:status 404}

      (str/blank? nav-path)
      {:status 302
       :headers {"Location" (or (:nav-path @!doc)
                                (->nav-path 'nextjournal.clerk.home))}}
      :else
      (if-let [file-or-ns (let [file-or-ns (->file-or-ns (maybe-add-extension nav-path))]
                            (when-not (forbidden-path? file-or-ns)
                              file-or-ns))]
        (do
          (try (show! (merge {:skip-history? true}
                             (select-keys opts [:expanded-paths :index :git/sha :git/url :git/prefix]))
                      file-or-ns)
               (catch ^:sci/error Exception e
                 (u/if-bb
                   (binding [*out* *err*]
                     (println
                      (str/join "\n" (sci.core/format-stacktrace (sci.core/stacktrace e)))))
                   nil)))
          {:status 200
           :headers {"Content-Type" "text/html" "Cache-Control" "no-store"}
           :body (view/->html {:doc (view/doc->viewer @!doc)
                               :resource->url @config/!resource->url
                               :render-router :serve
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
        "clerk_service_worker.js" (serve-resource (io/resource "public/clerk_service_worker.js"))
        ("_fs") (serve-file uri (str/replace uri "/_fs/" ""))
        "_ws" {:status 200 :body "upgrading..."}
        "favicon.ico" {:status 404}
        (serve-notebook req))
      (catch ^:sci/error Exception e
        {:status  500
         :body    (u/if-bb
                   (pr-str (sci.core/format-stacktrace (sci.core/stacktrace e)))
                   (with-out-str (pprint/pprint (Throwable->map e))))})
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
  (swap! !doc (fn [doc] (-> (or doc {})
                            (vary-meta assoc :status status)
                            (vary-meta update ::!send-status-future broadcast-status-debounced! status)))))

#_(clojure.java.browse/browse-url "http://localhost:7777")

;; # dynamic requirements
;; * load notebook without results
;; * allow page reload

(defn halt! []
  (when-let [{:keys [port instance]} @!server]
    @(httpkit/server-stop! instance)
    (println (str "Webserver running on " port ", stopped."))
    (reset! !server nil)
    (sci.nrepl/stop-nrepl-server!)))

#_(halt!)

(defn serve-sci-nrepl! [opts]
  (sci.nrepl/start-nrepl-server! (merge {:port 1339
                                         :send-fn send-nrepl!}
                                        opts)))

(defn serve! [{:as opts :keys [host port] :or {host "localhost" port 7777}}]
  (halt!)
  (try
    (reset! !server (assoc opts
                           :host host
                           :port port
                           :instance (httpkit/run-server #'app {:ip host :port port :legacy-return-value? false})))
    (println (format "Clerk webserver started on http://%s:%s ..." host port ))
    (when-let [render-nrepl-opts (:render-nrepl opts)]
      (serve-sci-nrepl! render-nrepl-opts))
    (catch java.net.BindException e
      (let [msg (format "Clerk webserver could not be started because port %d is not available. Stop what's running on port %d or specify a different port." port port)]
        (binding [*out* *err*]
          (println msg))
        (throw (ex-info msg {:port port} e))))))

#_(serve! {:port 7777})
#_(serve! {:port 7777 :paths ["notebooks/rule_30.clj"]})
#_(serve! {:port 7777 :paths ["notebooks/rule_30.clj" "notebooks/links.md"]})
#_(serve! {:port 7777 :paths ["notebooks/rule_30.clj"] :index "notebooks/links.md"})
#_(serve! {:port 7777 :paths ["notebooks/rule_30.clj" "book.clj"]})
#_(serve! {:port 7777 :paths ["notebooks/rule_30.clj" "notebooks/links.md" "notebooks/markdown.md" "index.clj"]})
#_(serve! {:port 7777 :host "0.0.0.0"})
