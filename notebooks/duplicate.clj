(ns duplicate
  (:require
   [nextjournal.clerk.webserver :as webserver]
   [nextjournal.clerk :as clerk]))


(+ 1 1)

(def a 1)

(def a 2)

(def b (+ a a))

(comment
  (reset! webserver/!doc {})
  (clerk/clear-cache!)
  (clerk/serve! {:browse? true})
  (clerk/show! "notebooks/duplicate.clj"))
