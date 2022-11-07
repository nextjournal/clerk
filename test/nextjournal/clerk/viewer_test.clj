(ns nextjournal.clerk.viewer-test
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [clojure.walk :as w]
            [matcher-combinators.test :refer [match?]]
            [nextjournal.clerk.builder :as builder]
            [nextjournal.clerk.viewer :as v]
            [nextjournal.clerk.eval :as eval]))

(defn present+fetch
  ([value] (present+fetch {} value))
  ([opts value]
   (let [desc (v/present value opts)
         elision (v/find-elision desc)
         more (v/present value elision)]
     (v/desc->values (v/merge-presentations desc more elision)))))

(deftest normalize-table-data
  (testing "works with sorted-map"
    (is (= {:head ["A" "B"]
            :rows [["Aani" "Baal"] ["Aaron" "Baalath"]]}
           (v/normalize-table-data (into (sorted-map) {"B" ["Baal" "Baalath"]
                                                       "A" ["Aani" "Aaron"]}))))))

(deftest resolve-elision
  (testing "range"
    (let [value (range 30)]
      (is (= value (present+fetch value)))))

  (testing "nested range"
    (let [value [(range 30)]]
      (is (= value (present+fetch value)))))

  (testing "string"
    (let [value (str/join (map #(str/join (repeat 80 %)) ["a" "b"]))]
      ;; `str/join` is needed here because elided strings get turned into vector of segments
      (is (= value (str/join (present+fetch value))))))

  (testing "deep vector"
    (let [value (reduce (fn [acc i] (vector acc)) :fin (range 30 0 -1))]
      (is (= value (present+fetch {:budget 21} value)))))

  (testing "deep vector with element before"
    (let [value (reduce (fn [acc i] (vector i acc)) :fin (range 15 0 -1))]
      (is (= value (present+fetch {:budget 21} value)))))

  (testing "deep vector with element after"
    (let [value (reduce (fn [acc i] (vector acc i)) :fin (range 20 0 -1))]
      (is (= value (present+fetch {:budget 21} value)))))

  (testing "deep vector with elements around"
    (let [value (reduce (fn [acc i] (vector i acc (inc i))) :fin (range 10 0 -1))]
      (is (= value (present+fetch {:budget 21} value)))))

  ;; TODO: fit table viewer into v/desc->values
  (testing "table"
    (let [value {:a (range 30) :b (range 30)}]
      (is (= (vec (vals (v/normalize-table-data value)))
             (present+fetch (v/table value)))))))

(deftest apply-viewers
  (testing "selects number viewer"
    (is (match? {:nextjournal/value 42
                 :nextjournal/viewer {:pred fn?}}
                (v/apply-viewers 42))))

  (testing "html viewer has no default width"
    (is (nil? (:nextjournal/width (v/apply-viewers (v/html [:h1 "hi"]))))))

  (testing "hiccup viewer width can be overriden"
    (is (= :wide
           (:nextjournal/width (v/apply-viewers (v/html {:nextjournal.clerk/width :wide} [:h1 "hi"]))))))

  (testing "table viewer defaults to wide width"
    (is (= :wide
           (:nextjournal/width (v/apply-viewers (v/table {:a [1] :b [2] :c [3]}))))))

  (testing "table viewer (with :transform-fn) width can be overriden"
    (is (= :full
           (:nextjournal/width (v/apply-viewers (v/table {:nextjournal.clerk/width :full} {:a [1] :b [2] :c [3]})))))))


(deftest present
  (testing "only transform-fn can select viewer"
    (is (match? {:nextjournal/value [:div.viewer-markdown
                                     [:p [:span "Hello "] [:em [:span "markdown"]] [:span "!"]]]
                 :nextjournal/viewer {:name :html-}}
                (v/present (v/with-viewer {:transform-fn (comp v/md v/->value)}
                             "Hello _markdown_!")))))

  (testing "works with sorted-map which can throw on get & contains?"
    (v/present (into (sorted-map) {'foo 'bar})))

  (testing "doesn't throw on bogus input"
    (is (match? {:nextjournal/value nil, :nextjournal/viewer {:name :html}}
                (v/present (v/html nil)))))

  (testing "opts are not propagated to children during presentation"
    (let [count-opts (fn [o]
                       (let [c (atom 0)]
                         (w/postwalk (fn [f] (when (= :nextjournal/opts f) (swap! c inc)) f) o)
                         @c))]
      (let [presented (v/present (v/col {:nextjournal.clerk/opts {:width 150}} 1 2 3))]
        (is (= {:width 150} (:nextjournal/opts presented)))
        (is (= 1 (count-opts presented))))

      (let [presented (v/present (v/table {:col1 [1 2] :col2 '[a b]}))]
        (is (= {:num-cols 2 :number-col? #{0}} (:nextjournal/opts presented)))
        (is (= 1 (count-opts presented)))))))

(deftest assign-closing-parens
  (testing "closing parenthesis are moved to right-most children in the tree"
    (let [before (#'v/present* (assoc (v/ensure-wrapped-with-viewers {:a [1 '(2 3 #{4})]
                                                                      :b '([5 6] 7 8)}) :path []))
          after (v/assign-closing-parens before)]

      (is (= "}"
             (-> before
                 (get-in (v/path-to-value [0 1 1]))
                 (get 2)
                 v/->viewer
                 :closing-paren)))
      (is (= ")"
             (-> before
                 (get-in (v/path-to-value [1]))
                 (get 1)
                 v/->viewer
                 :closing-paren)))

      (is (= '( "}" ")" "]")
             (-> after
                 (get-in (v/path-to-value [0 1 1]))
                 (get 2)
                 v/->viewer
                 :closing-paren)))
      (is (= '(")" "}")
             (-> after
                 (get-in (v/path-to-value [1]))
                 (get 1)
                 v/->viewer
                 :closing-paren))))))

(deftest doc->viewer
  (testing "Doc options are propagated to blob processing"
    (let [doc->viewer (resolve 'nextjournal.clerk.view/doc->viewer)
          test-doc (eval/eval-string "(java.awt.image.BufferedImage. 20 20 1)")
          tree-re-find (fn [data re] (->> data
                                          (tree-seq coll? seq)
                                          (filter string?)
                                          (filter (partial re-find re))))]

      (is (not-empty (tree-re-find (doc->viewer {:inline-results? true
                                                 :bundle? true
                                                 :out-path builder/default-out-path} test-doc)
                                   #"data:image/png;base64")))

      (is (not-empty (tree-re-find (doc->viewer {:inline-results? true
                                                 :bundle? false
                                                 :out-path builder/default-out-path} test-doc)
                                   #"\"_data/.+\.png\""))))))
