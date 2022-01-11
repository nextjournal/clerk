(ns nextjournal.clerk.viewer-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]
            [matcher-combinators.matchers :as m]
            [matcher-combinators.test :refer [match?]]
            [nextjournal.clerk.viewer :as viewer]))

(deftest assign-closing-parens
  (testing "closing parenthesis are moved to right-most children in the tree"
    (let [before (viewer/describe {:a [1 '(2 3 #{4})]
                                   :b '([5 6] 7 8)}
                                  {:viewers (viewer/get-viewers nil)}
                                  [])
          after (viewer/assign-closing-parens before)]

      (is (= "}"
             (-> before
                 (get-in (viewer/path-to-value [0 1 1]))
                 (get 2)
                 viewer/viewer
                 :closing-paren)))
      (is (= ")"
             (-> before
                 (get-in (viewer/path-to-value [1]))
                 (get 1)
                 viewer/viewer
                 :closing-paren)))

      (is (= '("}" ")" "]")
             (-> after
                 (get-in (viewer/path-to-value [0 1 1]))
                 (get 2)
                 viewer/viewer
                 :closing-paren)))
      (is (= '(")" "}")
             (-> after
                 (get-in (viewer/path-to-value [1]))
                 (get 1)
                 viewer/viewer
                 :closing-paren))))))

(deftest wrapped-with-viewer
  (is (match? #:nextjournal{:value {:one :two}
                            :viewer {:pred (m/equals map?)
                                     :name :map
                                     :render-fn 'v/map-viewer
                                     :closing-paren "}"
                                     :opening-paren "{"
                                     :fetch-opts {:n 10}}}
              (viewer/wrapped-with-viewer {:one :two})))
  (is (match? #:nextjournal{:value [1 2 3]
                            :viewer {:pred (m/equals vector?)
                                     :render-fn 'v/coll-viewer
                                     :fetch-opts {:n 20}
                                     :closing-paren "]"
                                     :opening-paren "["}}
              (viewer/wrapped-with-viewer [1 2 3])))
  (is (match? #:nextjournal{:value '(0 1 2)
                            :viewer
                            {:pred (m/equals sequential?)
                             :render-fn 'v/coll-viewer
                             :opening-paren "("
                             :closing-paren ")"
                             :fetch-opts {:n 20}}}
              (viewer/wrapped-with-viewer (range 3))))

  (let [f (io/file "notebooks")]
    (is (match? #:nextjournal{:value (pr-str f)
                              :viewer
                              {:pred any?
                               :transform-fn (m/equals pr-str)
                               :render-fn any?}}
                (viewer/wrapped-with-viewer f))))
  (is (match? #:nextjournal{:value "# Hello"
                            :viewer
                            {:name :markdown
                             :render-fn 'v/markdown-viewer
                             :fetch-fn (m/equals viewer/fetch-all)}}
              (viewer/wrapped-with-viewer (viewer/md "# Hello")))))

(deftest describe
  (is (match? {:nextjournal/value 123
               :nextjournal/viewer {:render-fn
                                    {:form '(fn [x] (v/html
                                                     [:span.syntax-number.inspected-value
                                                      (if (js/Number.isNaN x) "NaN" (str x))]))}}}
              (viewer/describe 123)))

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
              (viewer/describe {:hello [1 2 3]}))))
