(ns nextjournal.clerk.webserver
  (:require [clojure.string :as str]
            [clojure.pprint :as pprint]
            [clojure.edn :as edn]
            [org.httpkit.server :as httpkit]
            [nextjournal.clerk.view :as view]
            [nextjournal.clerk.viewer :as v]
            [lambdaisland.uri :as uri]))

(def help-doc
  [{:type :markdown :text "Use `nextjournal.clerk/show!` to make your notebook appear…"}])

(defonce !clients (atom #{}))
(defonce !doc (atom help-doc))


#_(view/doc->viewer @!doc)
#_(reset! !doc help-doc)

(defn broadcast! [msg]
  (doseq [ch @!clients]
    (httpkit/send! ch (view/->edn msg))))

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

(defn serve-blob [{:keys [uri query-string]}]
  (let [blob->result (meta @!doc)
        blob-id (str/replace uri "/_blob/" "")]
    (if (contains? blob->result blob-id)
      {:status 200
       #_#_ ;; leaving this out for now so I can open it directly
       :headers {"Content-Type" "application/edn"}
       :body (view/->edn (v/fetch (blob->result blob-id) (get-fetch-opts query-string)))}
      {:status 404})))

(defn app [{:as req :keys [uri]}]
  (if (:websocket? req)
    (httpkit/as-channel req {:on-open (fn [ch]
                                        (swap! !clients conj ch)
                                        (httpkit/send! ch (-> @!doc view/doc->viewer view/->edn)))
                             :on-close (fn [ch reason]
                                         (pr :on-close ch reason)
                                         (swap! !clients disj ch))})
    (try
      (case (get (re-matches #"/([^/]*).*" uri) 1)
        ("css" "js") {:status 302
                      :headers {"Location" (str "http://localhost:7778" uri)}}
        "_blob" (serve-blob req)
        "_ws" {:status 200 :body "upgrading..."}
        {:status  200
         :headers {"Content-Type" "text/html"}
         :body    (view/doc->html @!doc)})
      (catch Throwable e
        {:status  500
         :body    (with-out-str (pprint/pprint (Throwable->map e)))}))))

(defn update-doc! [doc]
  (broadcast! (view/doc->viewer (reset! !doc doc))))

(defn show-error! [e]
  (broadcast! (view/ex->viewer e)))

#_(clojure.java.browse/browse-url "http://localhost:7777")

;; # dynamic requirements
;; * load notebook without results
;; * allow page reload

(defonce server (atom nil))

(defn start! [{:keys [port] :or {port 7777}}]
  (println "Starting server on " port "...")
  (if @server
    (println "Server already started")
    (try
      (reset! server (httpkit/run-server #'app {:port port}))
      (catch java.net.BindException _e
        (println "Port ""not avaible, server not started!")))))

(add-tap broadcast!)

#_(tap> (shuffle (range 100)))
