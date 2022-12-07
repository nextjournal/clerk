(ns nextjournal.clerk.webserver
  (:require [babashka.fs :as fs]
            [clojure.edn :as edn]
            [clojure.pprint :as pprint]
            [clojure.string :as str]
            [editscript.core :as editscript]
            [nextjournal.clerk.view :as view]
            [nextjournal.clerk.viewer :as v]
            [org.httpkit.server :as httpkit]))

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
(defonce !error (atom nil))
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

(defn serve-blob [{:as doc :keys [blob->result ns]} {:keys [blob-id fetch-opts]}]
  (when-not ns
    (throw (ex-info "namespace must be set" {:doc doc})))
  (if (contains? blob->result blob-id)
    (let [result (v/apply-viewer-unwrapping-var-from-def (blob->result blob-id))
          desc (v/present (v/ensure-wrapped-with-viewers
                           (v/get-viewers ns result)
                           (v/->value result)) ;; TODO understand why this unwrapping fixes lazy loaded table viewers
                          fetch-opts)]
      (if (contains? desc :nextjournal/content-type)
        {:body (v/->value desc)
         :content-type (:nextjournal/content-type desc)}
        {:body (v/->edn desc)}))
    {:status 404}))

(defn extract-blob-opts [{:as _req :keys [uri query-string]}]
  {:blob-id (str/replace uri "/_blob/" "")
   :fetch-opts (get-fetch-opts query-string)})

(defn serve-file [path {:as req :keys [uri]}]
  (let [file-or-dir (str path uri)
        file (when (fs/exists? file-or-dir)
               (cond-> file-or-dir
                 (fs/directory? file-or-dir) (fs/file "index.html")))
        extension (fs/extension file)]
    (if (fs/exists? file)
      {:status 200
       :headers (cond-> {"Content-Type" ({"css" "text/css"
                                          "html" "text/html"
                                          "png" "image/png"
                                          "jpg" "image/jpeg"
                                          "js" "application/javascript"} extension "text/html")}
                  (and (= "js" extension) (fs/exists? (str file ".map"))) (assoc "SourceMap" (str uri ".map")))
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

(def ws-handlers
  {:on-open (fn [ch] (swap! !clients conj ch))
   :on-close (fn [ch _reason] (swap! !clients disj ch))
   :on-receive (fn [sender-ch edn-string]
                 (binding [*ns* (or (:ns @!doc)
                                    (create-ns 'user))]
                   (let [{:as msg :keys [type]} (read-msg edn-string)]
                     (case type
                       :eval (do (send! ch (merge {:type :eval-reply :eval-id (:eval-id msg)}
                                                  (try {:reply (eval (:form msg))}
                                                       (catch Exception e
                                                         {:error (Throwable->map e)}))))
                                 (eval '(nextjournal.clerk/recompute!)))
                       :swap! (when-let [var (resolve (:var-name msg))]
                                (try
                                  (apply swap! @var (eval (:args msg)))
                                  (binding [*sender-ch* sender-ch]
                                    (eval '(nextjournal.clerk/recompute!)))
                                  (catch Exception ex
                                    (throw (doto (ex-info (str "Clerk cannot `swap!` synced var `" (:var-name msg) "`.") msg ex) show-error!)))))))))})

#_(do
    (apply swap! nextjournal.clerk.atom/my-state (eval '[update :counter inc]))
    (eval '(nextjournal.clerk/recompute!)))

(defn app [{:as req :keys [uri]}]
  (if (:websocket? req)
    (httpkit/as-channel req ws-handlers)
    (try
      (case (get (re-matches #"/([^/]*).*" uri) 1)
        "_blob" (serve-blob @!doc (extract-blob-opts req))
        ("build" "js") (serve-file "public" req)
        "_ws" {:status 200 :body "upgrading..."}
        {:status  200
         :headers {"Content-Type" "text/html"}
         :body    (view/doc->html (or @!doc (help-doc)) @!error)})
      (catch Throwable e
        {:status  500
         :body    (with-out-str (pprint/pprint (Throwable->map e)))}))))

#_(nextjournal.clerk/serve! {})

(defn present+reset! [doc]
  (let [presented (view/doc->viewer doc)]
    (reset! !doc (with-meta doc presented))
    presented))

(defn update-doc! [doc]
  (reset! !error nil)
  (broadcast! (if (= (:ns @!doc) (:ns doc))
                (let [old-viewer (meta @!doc)
                      patch (editscript/diff old-viewer (present+reset! doc))]
                  {:type :patch-state! :patch (editscript/get-edits patch)})
                {:type :set-state! :doc (present+reset! doc)})))

#_(update-doc! help-doc)

#_(clojure.java.browse/browse-url "http://localhost:7777")

;; # dynamic requirements
;; * load notebook without results
;; * allow page reload

(defonce !server (atom nil))

(defn halt! []
  (when-let [{:keys [port stop-fn]} @!server]
    (stop-fn)
    (println (str "Webserver running on " port ", stopped."))
    (reset! !server nil)))

#_(halt!)

(defn serve! [{:keys [port] :or {port 7777}}]
  (halt!)
  (try
    (reset! !server {:port port :stop-fn (httpkit/run-server #'app {:port port})})
    (println (str "Clerk webserver started on http://localhost:" port " ..."))
    (catch java.net.BindException _e
      (println "Port " port " not available, server not started!"))))

#_(serve! {:port 7777})
