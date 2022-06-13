(ns hash-fn
  (:require [nextjournal.clerk :as clerk]))

^{::clerk/hash-fn (constantly (slurp "notebooks/hello.clj"))}
(def contents
  (slurp "notebooks/hello.clj"))

