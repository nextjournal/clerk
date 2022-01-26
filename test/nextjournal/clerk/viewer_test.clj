(ns nextjournal.clerk.viewer-test
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [nextjournal.clerk.viewer :as v]))

(defn find-elision [desc]
  (first (filter (comp #{:elision} :name :nextjournal/viewer)
                 (tree-seq (comp vector? :nextjournal/value) :nextjournal/value desc))))

(defn describe+fetch [value]
  (let [desc (v/describe value {:budget 21})
        elision (find-elision desc)
        more (v/describe value (:nextjournal/value elision))]
    (v/desc->values (v/merge-descriptions desc more))))

(deftest merge-descriptions
  (testing "range"
    (let [value (range 30)]
      (is (= value (describe+fetch value)))))

  (testing "nested range"
    (let [value [(range 30)]]
      (is (= value (describe+fetch value)))))

  (testing "string"
    (let [value (str/join (map #(str/join (repeat 70 %)) ["a" "b"]))]
      ;; `str/join` is needed here because elided strings get turned into vector of segments
      (is (= value (str/join (describe+fetch value))))))

  (testing "deep vector"
    (let [value (reduce (fn [acc i] (vector acc)) :fin (range 30 0 -1))]
      (is (= value (describe+fetch value)))))

  (testing "deep vector with element before"
    (let [value (reduce (fn [acc i] (vector i acc)) :fin (range 15 0 -1))]
      (is (= value (describe+fetch value)))))

  (testing "deep vector with element after"
    (let [value (reduce (fn [acc i] (vector acc i)) :fin (range 20 0 -1))]
      (is (= value (describe+fetch value)))))

  (testing "deep vector with elements around"
    (let [value (reduce (fn [acc i] (vector i acc (inc i))) :fin (range 10 0 -1))]
      (is (= value (describe+fetch value))))))

(deftest assign-closing-parens
  (testing "closing parenthesis are moved to right-most children in the tree"
    (let [before (v/describe {:a [1 '(2 3 #{4})]
                              :b '([5 6] 7 8)}
                             {:viewers (v/get-viewers nil) :!budget (atom 20)}
                             [])
          after (v/assign-closing-parens before)]

      (is (= "}"
             (-> before
                 (get-in (v/path-to-value [0 1 1]))
                 (get 2)
                 v/viewer
                 :closing-paren)))
      (is (= ")"
             (-> before
                 (get-in (v/path-to-value [1]))
                 (get 1)
                 v/viewer
                 :closing-paren)))

      (is (= '( "}" ")" "]")
             (-> after
                 (get-in (v/path-to-value [0 1 1]))
                 (get 2)
                 v/viewer
                 :closing-paren)))
      (is (= '(")" "}")
             (-> after
                 (get-in (v/path-to-value [1]))
                 (get 1)
                 v/viewer
                 :closing-paren))))))
