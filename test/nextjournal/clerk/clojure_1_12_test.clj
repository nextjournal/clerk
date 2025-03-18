(ns nextjournal.clerk.clojure-1-12-test
  (:require [clojure.test :as t :refer [deftest is]]
            [nextjournal.clerk :as clerk]))

(deftest notebook-is-analyzed-without-errors-test
  (is (clerk/show! "notebooks/qualified_methods.clj")))
