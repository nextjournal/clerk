(ns nextjournal.clerk.hashing-test
  (:require [nextjournal.clerk.hashing :as h]
            [clojure.test :refer :all]))

(defmacro eval-in-ns [ns & body]
  `(let [current-ns# *ns*]
    (in-ns ~ns)
    ~@body
    (in-ns (.name current-ns#))))

(deftest no-cache?
  (testing "are variables set to no-cache?"
    (is (not (h/no-cache? '(rand-int 10))))
    (is (not (h/no-cache? '(def random-thing (rand-int 1000)))))
    (is (not (h/no-cache? '(defn random-thing [] (rand-int 1000)))))
    (is (h/no-cache? '(def ^:nextjournal.clerk/no-cache random-thing (rand-int 1000))))
    (is (h/no-cache? '(defn ^:nextjournal.clerk/no-cache random-thing [] (rand-int 1000))))
    (is (h/no-cache? '(defn ^{:nextjournal.clerk/no-cache true} random-thing [] (rand-int 1000))))
    (is (not (h/no-cache? '[defn ^:nextjournal.clerk/no-cache trick [] 1]))))

  (testing "is evaluating namespace set to no-cache?"
    (is (not (h/no-cache? '(rand-int 10))))

    (eval-in-ns 'nextjournal.clerk.hashing
      (is (h/no-cache? '(rand-int 10))))))
