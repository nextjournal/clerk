(ns user)

(require '[nextjournal.clerk :as clerk :refer [show!]]
         '[nextjournal.clerk.view]
         '[nextjournal.clerk.webserver :as webserver])

(webserver/start! {})

(defn toggle-dev! []
  (alter-var-root #'nextjournal.clerk.view/live-js? not))

(comment
  (toggle-dev!)

  (show! "notebooks/elements.clj"))
