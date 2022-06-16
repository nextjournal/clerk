(ns nextjournal.clerk.webserver
  (:require [clojure.string :as str]
            [clojure.pprint :as pprint]
            [clojure.edn :as edn]
            [org.httpkit.server :as httpkit]
            [nextjournal.clerk.view :as view]
            [nextjournal.clerk.viewer :as v]
            [nextjournal.markdown :as md]
            [lambdaisland.uri :as uri]))

(def help-doc
  {:blocks [{:type :markdown :doc (md/parse "Use `nextjournal.clerk/show!` to make your notebook appear…")}]})

(defonce !clients (atom #{}))
(defonce !doc (atom help-doc))
(defonce !error (atom nil))

#_(v/present (view/doc->viewer @!doc))
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

(defn get-fetch-opts [query-string]
  (-> query-string
      uri/query-string->map
      (update-if :n #(Integer/parseInt %))
      (update-if :offset #(Integer/parseInt %))
      (update-if :path #(edn/read-string %))))

#_(get-pagination-opts "")
#_(get-pagination-opts "foo=bar&n=42&start=20")

(defn serve-blob [{:as doc :keys [blob->result ns]} {:keys [blob-id fetch-opts]}]
  (when-not ns
    (throw (ex-info "namespace must be set" {:doc doc})))
  (if (contains? blob->result blob-id)
    (let [result (v/apply-viewer-unwrapping-var-from-def (blob->result blob-id))
          desc (v/present (v/ensure-wrapped-with-viewers
                           (v/get-viewers ns result)
                           (v/->value result))           ;; TODO understand why this unwrapping fixes lazy loaded table viewers
                          fetch-opts)]
      (if (contains? desc :nextjournal/content-type)
        {:status 200 :body (v/->value desc) :content-type (:nextjournal/content-type desc)}
        {:status 200 :body (v/->edn desc)}))
    {:status 404}))

(defn extract-blob-opts [{:as _req :keys [uri query-string] ::keys [path-prefix]}]
  {:blob-id (str/replace uri (str "/" path-prefix "_blob/") "")
   :fetch-opts (get-fetch-opts query-string)})

(defn ->root-path [{:as _req :keys [uri] ::keys [path-prefix]}]
  (->> (str/replace uri (re-pattern (str "^/" path-prefix)) "")
       (str "/")
       (re-matches #"/([^/]*).*")
       (second)))

#_(->root-path {:uri "/_blob/92387fksdjhdskufsdf"})
#_(->root-path {:uri "/clerk/_blob/92387fksdjhdskufsdf" ::path-prefix "clerk/"})

(defn app [{:as req :keys [uri websocket?] ::keys [path-prefix]}]
  (if websocket?
    (httpkit/as-channel req {:on-open    (fn [ch] (swap! !clients conj ch))
                             :on-close   (fn [ch _reason] (swap! !clients disj ch))
                             :on-receive (fn [_ch msg] (binding [*ns* (or (:ns @!doc)
                                                                          (create-ns 'user))]
                                                         (eval (read-string msg))
                                                         (eval '(nextjournal.clerk/recompute!))))})
    (try
      (case (->root-path req)
        "_blob" (serve-blob @!doc (extract-blob-opts req))
        "_ws" {:status 200 :body "upgrading..."}
        {:status  200
         :headers {"Content-Type" "text/html"}
         :body    (view/doc->html {:doc         @!doc
                                   :error       @!error
                                   :path-prefix path-prefix})})
      (catch Throwable e
        {:status 500
         :body   (with-out-str (pprint/pprint (Throwable->map e)))}))))

(defn wrap-path-prefix [handler {:keys [path-prefix]}]
  (fn [req]
    (handler (assoc req ::path-prefix path-prefix))))

(defn update-doc! [doc]
  (reset! !error nil)
  (broadcast! {:doc (view/doc->viewer (reset! !doc doc))}))

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

(defn serve! [{:keys [port] :or {port 7777}}]
  (halt!)
  (try
    (reset! !server {:port port :stop-fn (httpkit/run-server #'app {:port port})})
    (println (str "Clerk webserver started on " port "..."))
    (catch java.net.BindException _e
      (println "Port " port " not available, server not started!"))))

#_(start! {:port 7777})
