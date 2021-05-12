(ns observator.webserver
  (:require [org.httpkit.server :as httpkit]
            [observator.webview :as webview]))

(def !clients (atom #{}))
(def !doc (atom [{:type :markdown :text "waiting for `send-file`..."}]))

(defn broadcast! [msg]
  (doseq [ch @!clients]
    (httpkit/send! ch (webview/->edn msg))))

#_(broadcast! [{:random (rand-int 10000) :range (range 100)}])

(defn app [{:as req :keys [uri]}]
  (case (get (re-matches #"/([^/]*).*" uri) 1)
    "_ws" (if-not (:websocket? req)
            {:status 200 :body "upgrading..."}
            (httpkit/as-channel req {:on-open (fn [ch]
                                                (swap! !clients conj ch)
                                                (httpkit/send! ch (-> @!doc webview/doc->viewer webview/->edn)))
                                     :on-close (fn [ch reason]
                                                 (pr :on-close ch reason)
                                                 (swap! !clients disj ch))}))
    {:status  200
     :headers {"Content-Type" "text/html"}
     :body    (webview/doc->html @!doc)}))

(defn update-doc! [doc]
  (broadcast! (webview/doc->viewer (reset! !doc doc))))

;; # dynamic requirements
;; * load notebook without results
;; * allow page reload

(defonce server (atom nil))

(reset! server (httpkit/run-server #'app {:port 7777}))
(add-tap broadcast!)

#_(tap> (shuffle (range 100)))
