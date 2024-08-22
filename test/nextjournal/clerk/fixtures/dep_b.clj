(ns nextjournal.clerk.fixtures.dep-b
  (:require [nextjournal.clerk.fixtures.dep-a :as dep-a]))

(defn thing []
  (dep-a/some-function-with-defs-inside))
