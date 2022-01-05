(ns nextjournal.clerk.viewer-test
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [nextjournal.clerk.viewer :as v]))

(deftest merge-descriptions
  (testing "range"
    (let [value (range 30)
          desc (v/describe value)
          elision (peek (get-in desc (v/path-to-value [])))
          more (v/describe value (:nextjournal/value elision))]
      (is (= value (v/desc->values (v/merge-descriptions desc more))))))

  (testing "nested range"
    (let [value [(range 30)]
          desc (v/describe value)
          elision (peek (get-in desc (v/path-to-value [0])))
          more (v/describe value (:nextjournal/value elision))]
      (is (= value (v/desc->values (v/merge-descriptions desc more))))))

  (testing "string"
    (let [value (str/join (map #(str/join (repeat 70 %)) ["a" "b"]))
          desc (v/describe value)
          elision (peek (get-in desc (v/path-to-value [])))
          more (v/describe value (:nextjournal/value elision))]
      ;; `str/join` is needed here because elided strings get turned into vector of segments
      (is (= value (str/join (v/desc->values (v/merge-descriptions desc more)))))))

  )

(deftest assign-closing-parens
  (testing "closing parenthesis are moved to right-most children in the tree"
    (let [before (v/describe {:a [1 '(2 3 #{4})]
                              :b '([5 6] 7 8)}
                             {:viewers (v/get-viewers nil)}
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
