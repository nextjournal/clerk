(ns user)

(require '[nextjournal.clerk :as clerk :refer [show!]]
         '[nextjournal.clerk.webserver :as webserver])

(webserver/start! {})

(comment
  (show! "notebooks/elements.clj"))
