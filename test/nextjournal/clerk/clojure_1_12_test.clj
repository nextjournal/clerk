(ns nextjournal.clerk.clojure-1-12-test
  (:require [clojure.test :as t :refer [deftest is]]
            [nextjournal.clerk :as clerk]))

(when (>= (:minor *clojure-version*) 12)
  (deftest notebook-is-analyzed-without-errors-test
    (is (do (clerk/show! "notebooks/clojure_1_12.clj")
            true))))
