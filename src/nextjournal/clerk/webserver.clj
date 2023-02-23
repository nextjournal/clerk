(ns nextjournal.clerk.webserver
  (:require [babashka.fs :as fs]
            [clojure.edn :as edn]
            [clojure.pprint :as pprint]
            [clojure.set :as set]
            [clojure.string :as str]
            [editscript.core :as editscript]
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

(defonce !client-uid->send-fn (atom {}))
(defonce !doc (atom nil))
(defonce !error (atom nil))
(defonce !last-sender-ch (atom nil))

#_(view/doc->viewer @!doc)
#_(reset! !doc nil)

(def ^:dynamic *sender-channel-uid* nil)

(defn broadcast! [msg]
  (doseq [[ch send-fn] @!client-uid->send-fn]
    (when (not= @!last-sender-ch *sender-channel-uid*)
      (send-fn ch {:type :patch-state!
                   :patch []
                   :effects [(v/->ViewerEval (list 'nextjournal.clerk.render/set-reset-sync-atoms! (not= *sender-channel-uid* ch)))]}))
    (send-fn ch msg))
  (reset! !last-sender-ch *sender-channel-uid*))

#_(broadcast! [{:random (rand-int 10000) :range (range 100)}])

(defn update-if [m k f]
  (if (k m)
    (update m k f)
    m))

#_(update-if {:n "42"} :n #(Integer/parseInt %))

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
      (update-if :n #(Integer/parseInt %))
      (update-if :offset #(Integer/parseInt %))
      (update-if :path #(edn/read-string %))))

#_(get-fetch-opts "")
#_(get-fetch-opts "foo=bar&n=42&start=20")

(defn blob->presented [presented-doc]
  ;; TODO: store on doc?
  (into {}
        (comp (map :nextjournal/value)
              (filter map?)
              (map (juxt :nextjournal/blob-id :nextjournal/presented)))
        (:blocks (:nextjournal/value presented-doc))))

(defn serve-blob [{:as doc :keys [blob->result ns]} {:keys [blob-id fetch-opts]}]
  (when-not ns
    (throw (ex-info "namespace must be set" {:doc doc})))
  (if (contains? blob->result blob-id)
    (let [root-desc (get (blob->presented (meta doc)) blob-id)
          {:keys [present-elision-fn]} (meta root-desc)
          desc (present-elision-fn fetch-opts)]
      (if (contains? desc :nextjournal/content-type)
        {:body (v/->value desc)
         :content-type (:nextjournal/content-type desc)}
        {:body (v/->edn desc)}))
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

(defn show-error! [e]
  (broadcast! {:type :set-state! :error (reset! !error (v/present e))}))

(defn read-msg [s]
  (binding [*data-readers* v/data-readers]
    (try (read-string s)
         (catch Exception ex
           (throw (doto (ex-info (str "Clerk encountered the following error attempting to read an incoming message: "
                                      (ex-message ex))
                                 {:message s} ex) show-error!))))))

#_(pr-str (read-msg "#viewer-eval (resolve 'clojure.core/inc)"))

(defn watch-websocket-clients
  "Sync external websocket client state into the clients that clerk tracks.

  Allows these external clients to get clerk related websocket messages and broadcasts"
  ([watched-atom send-fn] (watch-websocket-clients watched-atom send-fn identity))
  ([watched-atom send-fn state-transform]
   (add-watch watched-atom :connected-uids
              (fn [_var-name _atom old-state new-state]
                (let [new-state (state-transform new-state)
                      old-state (state-transform old-state)]
                (swap! !client-uid->send-fn
                       (fn [clients] (merge
                                       (apply dissoc
                                              clients
                                              (set/difference old-state new-state))
                                       (into {}
                                             (map (fn [uid] [uid send-fn]))
                                             (set/difference new-state old-state))))))))))

(defn unwatch-websocket-clients [watched-atom]
  (remove-watch watched-atom :connected-uids))

(defn handle-eval [sender-ch-uid msg]
  (binding [*ns* (or (:ns @!doc)
                     (create-ns 'user))]
    (let [send-fn (get @!client-uid->send-fn
                       sender-ch-uid
                       (constantly (throw (ex-info "Websocket channel not registered as a client"
                                                   {:websocket-channel-uid sender-ch-uid :message msg}))))]
      (send-fn sender-ch-uid
               (merge {:type :eval-reply :eval-id (:eval-id msg)}
                      (try {:reply (eval (:form msg))}
                           (catch Exception e
                             {:error (Throwable->map e)})))))
    (eval '(nextjournal.clerk/recompute!))))

(defn handle-swap! [sender-ch-uid msg]
  (binding [*ns* (or (:ns @!doc)
                     (create-ns 'user))]
    (when-let [var (resolve (:var-name msg))]
      (try
        (binding [*sender-channel-uid* sender-ch-uid]
          (apply swap! @var (eval (:args msg))))
        (catch Exception ex
          (throw (doto (ex-info (str "Clerk cannot `swap!` synced var `" (:var-name msg) "`.") msg ex) show-error!)))))))

(defn clerk-ws-send-fn [ch-uid msg]
  (httpkit/send! ch-uid (v/->edn msg)))

(def ws-handlers
  {:on-open (fn [ch] (swap! !client-uid->send-fn assoc ch clerk-ws-send-fn))
   :on-close (fn [ch _reason] (swap! !client-uid->send-fn dissoc ch))
   :on-receive (fn [sender-ch-uid edn-string]
                 (let [{:as msg :keys [type]} (read-msg edn-string)]
                   (case type
                     :eval (handle-eval sender-ch-uid msg)
                     :swap! (handle-swap! sender-ch-uid msg))))})

#_(do
    (apply swap! nextjournal.clerk.atom/my-state (eval '[update :counter inc]))
    (eval '(nextjournal.clerk/recompute!)))

(defn app [{:as req :keys [uri]}]
  (if (:websocket? req)
    (httpkit/as-channel req ws-handlers)
    (try
      (case (get (re-matches #"/([^/]*).*" uri) 1)
        "_blob" (serve-blob @!doc (extract-blob-opts req))
        ("build" "js" "css") (serve-file uri (str "public" uri))
        ("_fs") (serve-file uri (str/replace uri "/_fs/" ""))
        "_ws" {:status 200 :body "upgrading..."}
        {:status 200
         :headers {"Content-Type" "text/html"}
         :body (view/doc->html {:doc (or @!doc (help-doc)) :error @!error})})
      (catch Throwable e
        {:status  500
         :body    (with-out-str (pprint/pprint (Throwable->map e)))}))))

#_(nextjournal.clerk/serve! {})

(defn sync-atom-changed [key atom old-state new-state]
  (eval '(nextjournal.clerk/recompute!)))

(defn present+reset! [doc]
  (let [presented (view/doc->viewer doc)
        sync-vars-old (v/extract-sync-atom-vars @!doc)
        sync-vars (v/extract-sync-atom-vars doc)]
    (doseq [sync-var (set/difference sync-vars sync-vars-old)]
      (add-watch @sync-var (symbol sync-var) sync-atom-changed))
    (doseq [sync-var (set/difference sync-vars-old sync-vars)]
      (remove-watch @sync-var (symbol sync-var)))
    (reset! !doc (with-meta doc presented))
    presented))

(defn update-doc! [doc]
  (reset! !error nil)
  (broadcast! (if (= (:ns @!doc) (:ns doc))
                (let [old-viewer (meta @!doc)
                      patch (editscript/diff old-viewer (present+reset! doc) {:algo :quick})]
                  {:type :patch-state! :patch (editscript/get-edits patch)})
                {:type :set-state! :doc (present+reset! doc)})))

#_(update-doc! help-doc)

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
