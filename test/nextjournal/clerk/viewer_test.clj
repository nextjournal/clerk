(ns nextjournal.clerk.viewer-test
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [matcher-combinators.test :refer [match?]]
            [nextjournal.clerk.viewer :as v]))

(defn prepare+fetch
  ([value] (prepare+fetch {} value))
  ([opts value]
   (let [desc (v/prepare value opts)
         elision (v/find-elision desc)
         more (v/prepare value elision)]
     (v/desc->values (v/resolve-elision desc more elision)))))

(deftest resolve-elision
  (testing "range"
    (let [value (range 30)]
      (is (= value (prepare+fetch value)))))

  (testing "nested range"
    (let [value [(range 30)]]
      (is (= value (prepare+fetch value)))))

  (testing "string"
    (let [value (str/join (map #(str/join (repeat 80 %)) ["a" "b"]))]
      ;; `str/join` is needed here because elided strings get turned into vector of segments
      (is (= value (str/join (prepare+fetch value))))))

  (testing "deep vector"
    (let [value (reduce (fn [acc i] (vector acc)) :fin (range 30 0 -1))]
      (is (= value (prepare+fetch {:budget 21} value)))))

  (testing "deep vector with element before"
    (let [value (reduce (fn [acc i] (vector i acc)) :fin (range 15 0 -1))]
      (is (= value (prepare+fetch {:budget 21} value)))))

  (testing "deep vector with element after"
    (let [value (reduce (fn [acc i] (vector acc i)) :fin (range 20 0 -1))]
      (is (= value (prepare+fetch {:budget 21} value)))))

  (testing "deep vector with elements around"
    (let [value (reduce (fn [acc i] (vector i acc (inc i))) :fin (range 10 0 -1))]
      (is (= value (prepare+fetch {:budget 21} value)))))

  ;; TODO: fit table viewer into v/desc->values
  (testing "table"
    (let [value {:a (range 30) :b (range 30)}]
      (is (= (vec (vals (v/normalize-table-data value)))
             (prepare+fetch (v/table value)))))))

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

(defn viewer-eval-inspect? [x] (= x (v/->viewer-eval 'v/inspect)))

(deftest prepare
  (testing "only transform-fn can select viewer"
    (is (match? {:nextjournal/value [:div.viewer-markdown
                                     [:p [:span "Hello "] [:em [:span "markdown"]] [:span "!"]]]
                 :nextjournal/viewer {:name :html-}}
                (v/prepare (v/with-viewer {:transform-fn (comp v/md v/->value)}
                              "Hello _markdown_!")))))

  (testing "works with sorted-map which can throw on get & contains?"
    (v/prepare (into (sorted-map) {'foo 'bar})))

  (testing "doesn't throw on bogus input"
    (is (match? {:nextjournal/value nil, :nextjournal/viewer {:name :html}}
                (v/prepare (v/html nil))))))

(deftest assign-closing-parens
  (testing "closing parenthesis are moved to right-most children in the tree"
    (let [before (#'v/prepare* (assoc (v/ensure-wrapped-with-viewers {:a [1 '(2 3 #{4})]
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
