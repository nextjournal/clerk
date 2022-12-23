(ns nextjournal.clerk.render.hashing-test
  (:require [clojure.test :refer [deftest is testing]]
            [nextjournal.clerk.render.hashing :as hashing]))

(deftest front-end-hash
  (testing "it computes a front-end-hash successfully"
    (is (string? (hashing/front-end-hash)))))
