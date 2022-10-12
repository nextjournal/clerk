(ns nextjournal.clerk.view-test
  (:require [clojure.test :refer :all]
            [nextjournal.clerk.view :as view]
            [nextjournal.clerk.eval :as eval]))

(deftest doc->viewer
  (testing "Doc options are propagated to blob processing"
    (let [test-doc (eval/eval-string "(java.awt.image.BufferedImage. 20 20 1)")
          tree-re-find (fn [data re] (->> data
                                          (tree-seq coll? seq)
                                          (filter string?)
                                          (filter (partial re-find re))))]

      (is (not-empty (tree-re-find (view/doc->viewer {:inline-results? true :bundle? true} test-doc)
                                   #"data:image/png;base64")))

      (is (not-empty (tree-re-find (view/doc->viewer {:inline-results? true :bundle? false} test-doc)
                                   #"\"_data/.+\.png\""))))))
