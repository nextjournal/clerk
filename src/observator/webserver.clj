(ns observator.webserver
  (:require [org.httpkit.server :as httpkit]
            [observator.webview :as webview]
            [observator.core :as observator]))

(defn app [{:as _req :keys [uri]}]
  (if-let [[_ file] (re-matches #"/notebook/(.*)" uri)]
    (let [doc (with-bindings {#'*ns* (find-ns 'observator.webserver)}
                (observator/eval-file file))]
      {:status  200
       :headers {"Content-Type" "text/html"}
       :body    (webview/doc->html doc)})
    {:status 404}))

(defonce server (atom nil))

(reset! server (httpkit/run-server #'app {:port 7777}))
