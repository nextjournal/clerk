(ns nextjournal.clerk.webserver
  (:require [babashka.fs :as fs]
            [clojure.edn :as edn]
            [clojure.pprint :as pprint]
            [clojure.string :as str]
            [nextjournal.clerk.view :as view]
            [nextjournal.clerk.viewer :as v]
            [nextjournal.markdown :as md]
            [org.httpkit.server :as httpkit]))

(def help-doc
  {:blocks [{:type :markdown :doc (md/parse "Use `nextjournal.clerk/show!` to make your notebook appear…")}]})

(defonce !clients (atom #{}))
(defonce !doc (atom help-doc))
(defonce !error (atom nil))

#_(view/doc->viewer @!doc)
#_(reset! !doc help-doc)

(defn broadcast! [msg]
  (doseq [ch @!clients]
    (httpkit/send! ch (v/->edn msg))))

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

(defn read-msg [s]
  (binding [*data-readers* v/data-readers]
    (read-string s)))

#_(pr-str (read-msg "#viewer-eval (resolve 'clojure.core/inc)"))

(def ws-handlers
  {:on-open (fn [ch] (swap! !clients conj ch))
   :on-close (fn [ch _reason] (swap! !clients disj ch))
   :on-receive (fn [sender-ch edn-string]
                 (binding [*ns* (or (:ns @!doc)
                                    (create-ns 'user))]
                   (let [{:as msg :keys [type]} (read-msg edn-string)]
                     (case type
                       :eval (do (eval (:form msg))
                                 (eval '(nextjournal.clerk/recompute!)))
                       :swap! (do
                                (apply swap! @(eval (:form (:var msg))) (eval (:form (:args msg))))
                                (doseq [ch @!clients :when (not= sender-ch ch)]
                                  (httpkit/send! ch edn-string))
                                (eval '(nextjournal.clerk/recompute!)))))))})

#_(do 
    (apply swap! nextjournal.clerk.atom/my-state (eval '[update :counter inc]))
    (eval '(nextjournal.clerk/recompute!)))

(defn app [{:as req :keys [uri resource-urls]}]
  (if (:websocket? req)
    (httpkit/as-channel req ws-handlers)
    (try
      (case (get (re-matches #"/([^/]*).*" uri) 1)
        "_blob" (serve-blob @!doc (extract-blob-opts req))
        ("build" "js") (serve-file "public" req)
        "_ws" {:status 200 :body "upgrading..."}
        {:status  200
         :headers {"Content-Type" "text/html"}
         :body    (view/doc->html @!doc {:error @!error
                                         :resource-urls resource-urls})})
      (catch Throwable e
        {:status  500
         :body    (with-out-str (pprint/pprint (Throwable->map e)))}))))

#_(nextjournal.clerk/serve! {})

(defn extract-viewer-evals [{:as _doc :keys [blocks]}]
  (into #{}
        (comp (keep #(-> % :result :nextjournal/value (v/get-safe :nextjournal/value)))
              (filter (every-pred v/viewer-eval? :remount?)))
        blocks))


#_(extract-viewer-evals @!doc)

(defn update-doc! [doc]
  (reset! !error nil)
  (broadcast! {:type :set-state!
               :remount? (not= (extract-viewer-evals @!doc)
                               (extract-viewer-evals doc))
               :doc (view/doc->viewer (reset! !doc doc))}))


#_(update-doc! help-doc)

(defn show-error! [e]
  (broadcast! {:error (reset! !error (v/present e))}))


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

(defn serve! [{:keys [port resource-urls] :or {port 7777}}]
  (halt!)
  (try
    (let [resource-urls (merge @config/!resource->url resource-urls)]
      (reset! !server {:port port :stop-fn (httpkit/run-server
                                            (fn [req]
                                              (#'app (assoc req :resource-urls resource-urls)))
                                            {:port port})}))
    (println (str "Clerk webserver started on http://localhost:" port " ..."))
    (catch java.net.BindException _e
      (println "Port " port " not available, server not started!"))))

#_(serve! {:port 7777})
