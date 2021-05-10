(ns observator.webserver
  (:require [org.httpkit.server :as httpkit]
            [org.httpkit.timer :as httpkit.timer]
            [observator.webview :as webview]
            [observator.core :as observator]))

(def !clients (atom #{}))

#_(doseq [ch @!clients]
    (httpkit/send! ch (webview/->edn [{:random (rand-int 10000) :range (range 100)}])))

(defn app [{:as req :keys [uri]}]
  (case (get (re-matches #"/([^/]*).*" uri) 1)
    "notebook" (let [[_ file] (re-matches #"/notebook/(.*)" uri)
                     doc (with-bindings {#'*ns* (find-ns 'observator.core)}
                           (observator/parse-file file))]
                 {:status  200
                  :headers {"Content-Type" "text/html"}
                  :body    (webview/doc->html doc)})
    "_ws" (if-not (:websocket? req)
            {:status 200 :body "upgrading..."}
            (httpkit/as-channel req {:on-open (fn [ch]
                                                (swap! !clients conj ch)
                                                ;; TODO: handle file
                                                (let [file "src/observator/demo.clj"
                                                      doc (with-bindings {#'*ns* (find-ns 'observator.core)}
                                                            (observator/eval-file file))]
                                                  (httpkit/send! ch (-> doc webview/doc->viewer webview/->edn))))
                                     :on-close (fn [ch reason]
                                                 (pr :on-close ch reason)
                                                 (swap! !clients disj ch))}))
    {:status 302
     :headers {"Location" "/notebook/src/observator/demo.clj"}}))

;; # dynamic requirements
;; * load notebook without results
;; * allow page reload

(defonce server (atom nil))

(reset! server (httpkit/run-server #'app {:port 7777}))
