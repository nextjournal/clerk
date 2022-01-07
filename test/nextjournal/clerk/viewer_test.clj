(ns nextjournal.clerk.viewer-test
  (:require [clojure.test :refer :all]
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

      (is (= '( "}" ")" "]")
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
