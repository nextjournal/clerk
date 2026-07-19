(ns nextjournal.clerk.render.hashing-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [nextjournal.clerk.render.hashing :as hashing]))

(deftest front-end-hash
  (testing "it computes a front-end-hash successfully"
    (let [debug-dejavu (System/getProperty "nextjournal.dejavu.debug")]
      (when-not debug-dejavu
        (System/setProperty "nextjournal.dejavu.debug" "1"))
      (is (not (str/blank? (hashing/front-end-hash))))
      (when-not debug-dejavu
        (System/clearProperty "nextjournal.dejavu.debug")))))
