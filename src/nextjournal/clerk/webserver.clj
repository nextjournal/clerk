(ns nextjournal.clerk.webserver
  (:require [org.httpkit.server :as httpkit]
            [nextjournal.clerk.view :as view]))

(def help-doc
  [{:type :markdown :text "Use `nextjournal.clerk/show!` to make your notebook appear…"}])

(def !clients (atom #{}))
(def !doc (atom help-doc))

#_(reset! !doc help-doc)

(defn broadcast! [msg]
  (doseq [ch @!clients]
    (httpkit/send! ch (view/->edn msg))))

#_(broadcast! [{:random (rand-int 10000) :range (range 100)}])

(defn app [{:as req :keys [uri]}]
  (case (get (re-matches #"/([^/]*).*" uri) 1)
    "js" {:status 302
          :headers {"Location" (str "http://localhost:8003" uri)}}
    "_ws" (if-not (:websocket? req)
            {:status 200 :body "upgrading..."}
            (httpkit/as-channel req {:on-open (fn [ch]
                                                (swap! !clients conj ch)
                                                (httpkit/send! ch (-> @!doc view/doc->viewer view/->edn)))
                                     :on-close (fn [ch reason]
                                                 (pr :on-close ch reason)
                                                 (swap! !clients disj ch))}))
    {:status  200
     :headers {"Content-Type" "text/html"}
     :body    (view/doc->html @!doc)}))

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
