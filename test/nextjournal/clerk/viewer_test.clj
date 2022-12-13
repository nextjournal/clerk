(ns nextjournal.clerk.viewer-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [clojure.walk :as w]
            [matcher-combinators.test :refer [match?]]
            [nextjournal.clerk.builder :as builder]
            [nextjournal.clerk.eval :as eval]
            [nextjournal.clerk.view :as view]
            [nextjournal.clerk.viewer :as v]))

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
    (let [value (reduce (fn [acc _i] (vector acc)) :fin (range 30 0 -1))]
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


(deftest apply-viewer-unwrapping-var-from-def
  (def my-test-var [:h1 "hi"])
  (let [apply+get-value #(-> % v/apply-viewer-unwrapping-var-from-def :nextjournal/value :nextjournal/value)]
    (testing "unwraps var when viewer doens't opt out"
      (is (= my-test-var
             (apply+get-value {:nextjournal/value [:h1 "hi"]                                      :nextjournal/viewer v/html})
             (apply+get-value {:nextjournal/value {:nextjournal.clerk/var-from-def #'my-test-var} :nextjournal/viewer v/html})
             (apply+get-value {:nextjournal/value {:nextjournal.clerk/var-from-def #'my-test-var} :nextjournal/viewer v/html-viewer}))))

    (testing "leaves var wrapped when viewer opts out"
      (is (= {:nextjournal.clerk/var-from-def #'my-test-var}
             (apply+get-value {:nextjournal/value {:nextjournal.clerk/var-from-def #'my-test-var}
                               :nextjournal/viewer (assoc v/html-viewer :var-from-def? true)}))))))


(deftest resolve-aliases
  (testing "it resolves aliases"
    (is (= '[nextjournal.clerk.viewer/render-code
             nextjournal.clerk.render.hooks/use-callback
             nextjournal.clerk.render/render-code]
           (v/resolve-aliases {'v (find-ns 'nextjournal.clerk.viewer)
                               'my-hooks (create-ns 'nextjournal.clerk.render.hooks)}
                              '[v/render-code
                                my-hooks/use-callback
                                nextjournal.clerk.render/render-code])))))

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
  (testing "extraction of synced vars"
    (is (not-empty (-> (view/doc->viewer (eval/eval-string "(ns nextjournal.clerk.test.sync-vars (:require [nextjournal.clerk :as clerk]))
                                     ^::clerk/sync (def sync-me (atom {:a ['b 'c 3]}))"))
                       :nextjournal/value
                       :atom-var-name->state
                       :form
                       second)))

    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"Clerk can only sync values which can be round-tripped in EDN"
         (view/doc->viewer (eval/eval-string "(ns nextjournal.clerk.test.sync-vars (:require [nextjournal.clerk :as clerk]))
                                     ^::clerk/sync (def sync-me (atom {:a (fn [x] x)}))")))))

  (testing "Doc options are propagated to blob processing"
    (let [test-doc (eval/eval-string "(java.awt.image.BufferedImage. 20 20 1)")
          tree-re-find (fn [data re] (->> data
                                          (tree-seq coll? seq)
                                          (filter string?)
                                          (filter (partial re-find re))))]
      (is (not-empty (tree-re-find (view/doc->viewer {:inline-results? true
                                                      :bundle? true
                                                      :out-path builder/default-out-path} test-doc)
                                   #"data:image/png;base64")))

      (is (not-empty (tree-re-find (view/doc->viewer {:inline-results? true
                                                      :bundle? false
                                                      :out-path builder/default-out-path} test-doc)
                                   #"_data/.+\.png"))))))

(deftest ->edn
  (testing "normal symbols and keywords"
    (is (= "normal-symbol" (pr-str 'normal-symbol)))
    (is (= ":namespaced/keyword" (pr-str :namespaced/keyword))))

  (testing "unreadable symbols and keywords print as viewer-eval"
    (is (= "#viewer-eval (keyword \"with spaces\")"
           (pr-str (keyword "with spaces"))))
    (is (= "#viewer-eval (keyword \"with ns\" \"and spaces\")"
           (pr-str (keyword "with ns" "and spaces"))))
    (is (= "#viewer-eval (symbol \"with spaces\")"
           (pr-str (symbol "with spaces"))))
    (is (= "#viewer-eval (symbol \"with ns\" \"and spaces\")"
           (pr-str (symbol "with ns" "and spaces")))))

  (testing "splicing reader conditional prints normally (issue #338)"
    (is (= "?@" (pr-str (symbol "?@"))))))
