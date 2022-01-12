(ns nextjournal.clerk.viewer-test
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [matcher-combinators.matchers :as m]
            [matcher-combinators.test :refer [match?]]
            [nextjournal.clerk.viewer :as v]))

(defn find-elision [desc]
  (first (filter (comp #{:elision} :nextjournal/viewer)
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

      (is (= '("}" ")" "]")
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

(deftest wrapped-with-viewer
  (is (match? #:nextjournal{:value {:one :two}
                            :viewer {:pred (m/equals map?)
                                     :name :map
                                     :render-fn 'v/map-viewer
                                     :closing-paren "}"
                                     :opening-paren "{"
                                     :fetch-opts {:n 10}}}
              (v/wrapped-with-viewer {:one :two})))
  (is (match? #:nextjournal{:value [1 2 3]
                            :viewer {:pred (m/equals vector?)
                                     :render-fn 'v/coll-viewer
                                     :fetch-opts {:n 20}
                                     :closing-paren "]"
                                     :opening-paren "["}}
              (v/wrapped-with-viewer [1 2 3])))
  (is (match? #:nextjournal{:value '(0 1 2)
                            :viewer
                            {:pred (m/equals sequential?)
                             :render-fn 'v/coll-viewer
                             :opening-paren "("
                             :closing-paren ")"
                             :fetch-opts {:n 20}}}
              (v/wrapped-with-viewer (range 3))))

  (let [f (io/file "notebooks")]
    (is (match? #:nextjournal{:value (pr-str f)
                              :viewer
                              {:pred any?
                               :transform-fn (m/equals pr-str)
                               :render-fn any?}}
                (v/wrapped-with-viewer f))))
  (is (match? #:nextjournal{:value "# Hello"
                            :viewer
                            {:name :markdown
                             :render-fn 'v/markdown-viewer
                             :fetch-fn (m/equals v/fetch-all)}}
              (v/wrapped-with-viewer (v/md "# Hello")))))

(deftest describe
  (is (match? {:nextjournal/value 123
               :nextjournal/viewer {:render-fn
                                    {:form '(fn [x] (v/html
                                                     [:span.syntax-number.inspected-value
                                                      (if (js/Number.isNaN x) "NaN" (str x))]))}}}
              (v/describe 123)))

  (is (match? {:nextjournal/value [{:nextjournal/value [{:nextjournal/value :hello}
                                                        {:nextjournal/value [{:nextjournal/value 1
                                                                              :nextjournal/viewer any?}
                                                                             {:nextjournal/value 2
                                                                              :nextjournal/viewer any?}
                                                                             {:nextjournal/value 3
                                                                              :nextjournal/viewer any?}]
                                                         :nextjournal/viewer {:render-fn {:form 'v/coll-viewer}}}]
                                    :nextjournal/viewer {:name :map-entry}}]
               :nextjournal/viewer {:name :map}}
              (v/describe {:hello [1 2 3]}))))
