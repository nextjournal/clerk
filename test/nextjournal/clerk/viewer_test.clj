(ns nextjournal.clerk.viewer-test
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [clojure.walk :as w]
            [matcher-combinators.test :refer [match?]]
            [nextjournal.clerk.builder :as builder]
            [nextjournal.clerk.config :as config]
            [nextjournal.clerk.eval :as eval]
            [nextjournal.clerk.eval-test :as eval-test]
            [nextjournal.clerk.view :as view]
            [nextjournal.clerk.viewer :as v]))

(defn resolve-elision [desc]
  (let [elision (v/find-elision desc)
        _ (when-not elision
            (throw (ex-info "no elision found" {:decs desc})))
        {:keys [present-elision-fn]} (meta desc)
        more (present-elision-fn elision)]
    (v/merge-presentations desc more elision)))

(defn present+fetch [value]
  (v/desc->values (resolve-elision (v/present value))))

(deftest normalize-table-data
  (testing "works with sorted-map"
    (is (= {:head ["A" "B"]
            :rows [["Aani" "Baal"] ["Aaron" "Baalath"]]}
           (v/normalize-table-data (into (sorted-map) {"B" ["Baal" "Baalath"]
                                                       "A" ["Aani" "Aaron"]})))))
  (testing "works with infinte lazy seqs"
    (binding [config/*bounded-count-limit* 1000]
      (is (v/present (v/normalize-table-data (repeat [1 2 3])))))

    (binding [config/*bounded-count-limit* 1000]
      (is (v/present (v/normalize-table-data (repeat {:a 1 :b 2})))))

    (binding [config/*bounded-count-limit* 1000]
      (is (v/present (v/normalize-table-data {:a (range) :b (range 80)}))))))

(deftest merge-presentations
  (testing "range"
    (let [value (range 30)]
      (is (= value (present+fetch value)))))

  (testing "nested ranges"
    (let [value [(range 30)]]
      (is (= value (present+fetch value))))
    (let [value {:hello (range 30)}]
      (is (= value (present+fetch value)))))

  (testing "string"
    (let [value (str/join (map #(str/join (repeat 80 %)) ["a" "b"]))]
      ;; `str/join` is needed here because elided strings get turned into vector of segments
      (is (= value (str/join (present+fetch value))))))

  (testing "deep vector"
    (let [value (reduce (fn [acc _i] (vector acc)) :fin (range 30 0 -1))]
      (is (= value (present+fetch {:nextjournal/budget 21 :nextjournal/value value})))))

  (testing "deep vector with element before"
    (let [value (reduce (fn [acc i] (vector i acc)) :fin (range 15 0 -1))]
      (is (= value (present+fetch {:nextjournal/budget 21 :nextjournal/value value})))))

  (testing "deep vector with element after"
    (let [value (reduce (fn [acc i] (vector acc i)) :fin (range 20 0 -1))]
      (is (= value (present+fetch {:nextjournal/budget 21 :nextjournal/value value})))))

  (testing "deep vector with elements around"
    (let [value (reduce (fn [acc i] (vector i acc (inc i))) :fin (range 10 0 -1))]
      (is (= value (present+fetch {:nextjournal/budget 21 :nextjournal/value value})))))

  ;; TODO: fit table viewer into v/desc->values
  (testing "table"
    (let [value {:a (range 30) :b (range 30)}]
      (is (= (vec (vals (v/normalize-table-data value)))
             (present+fetch (v/table value))))))

  (testing "resolving multiple elisions"
    (let [value (reduce (fn [acc i] (vector i acc)) :fin (range 15 0 -1))]
      (is (= value (v/desc->values (-> (v/present {:nextjournal/budget 11 :nextjournal/value value}) resolve-elision resolve-elision))))))

  (testing "elision inside html"
    (let [value (v/html [:div [:ul [:li {:nextjournal/value (range 30)}]]])]
      (is (= (v/->value value) (v/->value (v/desc->values (resolve-elision (v/present value))))))))

  (testing "resolving elided blobs"
    (let [{:nextjournal/keys [presented blob-id]}
          (-> (eval/eval-string "(ns nextjournal.clerk.viewer-test.elision-and-images
                             (:require [nextjournal.clerk :as clerk]))
                           (clerk/image \"trees.png\")")
                     view/doc->viewer
                     :nextjournal/value
                     :blocks second :nextjournal/value second
                     :nextjournal/value)]

      ;; n.c.viewer/process-blobs replaces bytes with an elision containing path and blob-id
      (is (= {:blob-id blob-id :path [1]} (:nextjournal/value presented)))
      (is (= "image/png" (:nextjournal/content-type presented)))

      ;; this is the mechanism that let image contents be resolved via n.c.webserver/serve-blob
      ;; see also n.c.render/url-for
      (is (bytes? (:nextjournal/value
                   ((:present-elision-fn (meta presented))
                    (select-keys (:nextjournal/value presented) [:path]))))))))

(deftest default-viewers
  (testing "viewers have names matching vars"
    (doseq [[viewer-name viewer] (into {}
                                       (map (juxt :name (comp deref resolve :name)))
                                       v/default-viewers)]
      (is (= viewer-name (:name viewer))))))

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

(deftest presenting-wrapped-values
  (testing "apply-viewers is invariant on wrapped values"
    (is (= (v/apply-viewers (v/with-viewer v/number-viewer 123))
           (v/apply-viewers {:nextjournal/value (v/with-viewer v/number-viewer 123)})
           (v/apply-viewers {:nextjournal/value {:nextjournal/value (v/with-viewer v/number-viewer 123)}}))))

  (testing "present is invariant on wrapped values"
    (is (= (v/present (v/with-viewer v/number-viewer 123))
           (v/present {:nextjournal/value (v/with-viewer v/number-viewer 123)})
           (v/present {:nextjournal/value {:nextjournal/value (v/with-viewer v/number-viewer 123)}})))

    (is (= (v/present (v/with-viewer v/html-viewer [:h1 "ahoi"]))
           (v/present {:nextjournal/value (v/with-viewer v/html-viewer [:h1 "ahoi"])})
           (v/present {:nextjournal/value {:nextjournal/value (v/with-viewer v/html-viewer [:h1 "ahoi"])}}))))

  (testing "invalid `:render-fn` throws error"
    (is (thrown? clojure.lang.ExceptionInfo
                 (v/present (v/with-viewer {:render-fn (fn [x] [:h1 x])} "Mike"))))))

(deftest render-fn-edn
  (testing "can be round-tripped to edn"
    (binding [*data-readers* v/data-readers]
      (let [render-fn (v/->render-fn 'inc)]
        (is (= render-fn
               (read-string (pr-str render-fn)))))
      (let [render-fn+ (v/->render-fn+opts {:render-evaluator :cherry} 'inc)]
        (is (= render-fn+
               (read-string (pr-str render-fn+))))))))

(deftest present-exceptions
  (testing "can represent ex-data in a readable way"
    (binding [*data-readers* v/data-readers]

      (is (-> (eval-test/eval+extract-doc-blocks "(ex-info \"💥\"  {:foo 123 :boom (fn boom [x] x)})")
              second :nextjournal/value :nextjournal/presented
              v/->edn
              read-string))

      (is (-> (eval-test/eval+extract-doc-blocks "(ex-info \"💥\"  {:foo 123 :boom (fn boom [x] x)} (RuntimeException. \"no way\"))")
              second :nextjournal/value :nextjournal/presented
              v/->edn
              read-string)))))

(deftest present-functions
  (testing "can represent functions in a readable way"
    (binding [*data-readers* v/data-readers]
      (is (-> (eval-test/eval+extract-doc-blocks "(fn boom [x] x)")
              second :nextjournal/value :nextjournal/presented
              v/->edn
              read-string)))))

(deftest datafy-scope
  (is (= (ns-name *ns*)
         (v/datafy-scope *ns*)
         (v/datafy-scope (ns-name *ns*))))

  (is (= :default (v/datafy-scope :default)))

  (is (thrown? clojure.lang.ExceptionInfo (v/datafy-scope :default-2)))
  (is (thrown? clojure.lang.ExceptionInfo (v/datafy-scope :foo))) )

(deftest reset-viewers!
  (testing "namespace scope"
    (let [ns (create-ns 'nextjournal.clerk.viewer-test.random-ns-name)]
      (v/reset-viewers! ns [])
      (is (= [] (v/get-viewers ns)))))

  (testing "symbol scope"
    (v/reset-viewers! 'nextjournal.clerk.viewer-test.random-ns-name [{:render-fn 'foo}])
    (is (= [{:render-fn 'foo}] (v/get-viewers 'nextjournal.clerk.viewer-test.random-ns-name)))))

(def my-test-var [:h1 "hi"])

(deftest apply-viewer-unwrapping-var-from-def
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
    (is (match? {:nextjournal/value [:div.viewer.markdown-viewer.w-full.max-w-prose.px-8 {}
                                     ["h1" {:id "hello-markdown!"} [:<> "👋 Hello "] [:em [:<> "markdown"]] [:<> "!"]]]
                 :nextjournal/viewer {:name `v/markdown-node-viewer}}
                (v/present (v/with-viewer {:transform-fn (comp v/md v/->value)}
                             "# 👋 Hello _markdown_!")))))

  (testing "works with sorted-map which can throw on get & contains?"
    (v/present (into (sorted-map) {'foo 'bar})))

  (testing "doesn't throw on bogus input"
    (is (match? {:nextjournal/value nil, :nextjournal/viewer {:name `v/html-viewer}}
                (v/present (v/html nil)))))

  (testing "big ints and ratios are represented as strings (issue #335)"
    (is (match? {:nextjournal/value "1142497398145249635243N"}
                (v/present 1142497398145249635243N)))
    (is (match? {:nextjournal/value "10/33"}
                (v/present 10/33)))
    (is (match? {:nextjournal/value "9007199254740993"}
                (v/present 9007199254740993)))
    (is (match? {:nextjournal/value "-9007199254740993"}
                (v/present -9007199254740993))))

  (testing "opts are not propagated to children during presentation"
    (let [count-opts (fn [o]
                       (let [c (atom 0)]
                         (w/postwalk (fn [f] (when (= :nextjournal/render-opts f) (swap! c inc)) f) o)
                         @c))]
      (let [presented (v/present (v/col {:nextjournal.clerk/render-opts {:width 150}} 1 2 3))]
        (is (= {:width 150} (:nextjournal/render-opts presented)))
        (is (= 1 (count-opts presented))))

      (let [presented (v/present (v/table {:col1 [1 2] :col2 '[a b]}))]
        (is (= {:num-cols 2 :number-col? #{0}} (:nextjournal/render-opts presented)))
        (is (= 1 (count-opts presented))))))

  (testing "viewer opts are normalized"
    (is (= (v/desc->values (v/present {:nextjournal/value (range 10) :nextjournal/budget 3}))
           (v/desc->values (v/present {:nextjournal/value (range 10) :nextjournal.clerk/budget 3}))
           (v/desc->values (v/present (v/with-viewer {} {:nextjournal.clerk/budget 3} (range 10))))
           (v/desc->values (v/present {:nextjournal/budget 3, :nextjournal/value (range 10)}))
           (v/desc->values (v/present {:nextjournal/budget 3, :nextjournal/value (range 10)}))))))

(deftest mark-preserved
  (testing "leaves values unmodified"
    (let [val {:foo :bar :bar (range 3)}]
      (is (= val
             (v/->value (v/present (v/with-viewer {:transform-fn v/mark-presented} val))))))))

(deftest mark-preserve-keys
  (let [x {:foo :bar :bar [1 2 3] :third (range 3)}
        presented-x (v/->value (v/present (v/with-viewer {:transform-fn v/mark-preserve-keys} x)))]
    (testing "leaves keys unmodified"
      (is (= (keys x)
             (keys presented-x))))
    (testing "presents all values"
      (is (every? v/wrapped-value? (vals presented-x))))))

(defn find-presented-keys [presented-val]
  (into #{}
        (keep (fn [[k v]] (when (v/wrapped-value? v) k)))
        presented-val))

(deftest preserve-keys
  (let [x {:foo :bar :bar [1 2 3] :third (range 3)}
        presented-x (v/->value (v/present (v/with-viewer {:transform-fn (v/preserve-keys #{:foo :bar})} x)))]
    (testing "leaves keys unmodified"
      (is (= (keys x)
             (keys presented-x))))
    (testing "presents some values"
      (is (= #{:third}
             (find-presented-keys presented-x)
             (find-presented-keys (v/->value (v/present (v/with-viewer {:transform-fn (v/preserve-keys (complement #{:third}))} x)))))))))

(defn path-to-value [path]
  (conj (interleave path (repeat :nextjournal/value)) :nextjournal/value))

(deftest assign-closing-parens
  (testing "closing parenthesis are moved to right-most children in the tree"
    (let [before (#'v/present* (assoc (v/ensure-wrapped-with-viewers {:a [1 '(2 3 #{4})]
                                                                      :b '([5 6] 7 8)}) :path []))
          after (v/assign-closing-parens before)]

      (is (= "}"
             (-> before
                 (get-in (path-to-value [0 1 1]))
                 (get 2)
                 v/->viewer
                 :closing-paren)))
      (is (= ")"
             (-> before
                 (get-in (path-to-value [1]))
                 (get 1)
                 v/->viewer
                 :closing-paren)))

      (is (= '( "}" ")" "]")
             (-> after
                 (get-in (path-to-value [0 1 1]))
                 (get 2)
                 v/->viewer
                 :closing-paren)))
      (is (= '(")" "}")
             (-> after
                 (get-in (path-to-value [1]))
                 (get 1)
                 v/->viewer
                 :closing-paren))))))

(defn tree-re-find [data re]
  (->> data
       (tree-seq coll? seq)
       (filter string?)
       (filter (partial re-find re))))

(deftest doc->viewer
  (testing "extraction of synced vars"
    (is (not-empty (-> (view/doc->viewer (eval/eval-string "(ns nextjournal.clerk.test.sync-vars (:require [nextjournal.clerk :as clerk]))
                                     ^::clerk/sync (def sync-me (atom {:a ['b 'c 3]}))"))
                       :nextjournal/value
                       :atom-var-name->state)))

    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"Clerk can only sync values which can be round-tripped in EDN"
         (view/doc->viewer (eval/eval-string "(ns nextjournal.clerk.test.sync-vars (:require [nextjournal.clerk :as clerk]))
                                     ^::clerk/sync (def sync-me (atom {:a (fn [x] x)}))")))))

  (testing "Local images are served as blobs in show mode"
    (let [test-doc (eval/eval-string ";; Some inline image ![alt](trees.png) here.")]
      (is (not-empty (tree-re-find (view/doc->viewer test-doc) #"_fs/trees.png")))))

  (testing "Local images are inlined in single-file static builds"
    (let [test-doc (eval/eval-string ";; Some inline image ![alt](trees.png) here.")]
      (is (not-empty (tree-re-find (view/doc->viewer {:package :single-file} test-doc) #"data:image/png;base64")))))

  (testing "Local images are content addressed for default static builds"
    (let [test-doc (eval/eval-string ";; Some inline image ![alt](trees.png) here.")]
      (is (not-empty (tree-re-find (view/doc->viewer {:package :directory :out-path (str (fs/temp-dir))} test-doc) #"_data/.+\.png")))))

  (testing "Doc options are propagated to blob processing"
    (let [test-doc (eval/eval-string "(java.awt.image.BufferedImage. 20 20 1)")]
      (is (not-empty (tree-re-find (view/doc->viewer {:package :single-file
                                                      :out-path builder/default-out-path} test-doc)
                                   #"data:image/png;base64")))

      (is (not-empty (tree-re-find (view/doc->viewer {:package :directory
                                                      :out-path builder/default-out-path} test-doc)
                                   #"_data/.+\.png")))))

  (testing "presentations are pure, result hashes are stable"
    (let [test-doc (eval/eval-string "(range 100)")]
      (is (= (view/doc->viewer {} test-doc)
             (view/doc->viewer {} test-doc)))))

  (testing "Setting custom options on results via metadata"
    (is (= :full
           (-> (eval-test/eval+extract-doc-blocks "^{:nextjournal.clerk/width :full} (nextjournal.clerk/html [:div])")
               second v/->value :nextjournal/presented :nextjournal/width)))

    (is (= [:rounded :bg-indigo-600 :font-bold]
           (-> (eval-test/eval+extract-doc-blocks "^{:nextjournal.clerk/css-class [:rounded :bg-indigo-600 :font-bold]} (nextjournal.clerk/table [[1 2][3 4]])")
               second
               v/->value :nextjournal/presented :nextjournal/css-class))))

  (testing "Setting custom options on results via viewer API"
    (is (= :full
           (-> (eval-test/eval+extract-doc-blocks "(nextjournal.clerk/html {:nextjournal.clerk/width :full} [:div])")
               second
               v/->value :nextjournal/presented :nextjournal/width)))
    (is (= [:rounded :bg-indigo-600 :font-bold]
           (-> (eval-test/eval+extract-doc-blocks "(nextjournal.clerk/table {:nextjournal.clerk/css-class [:rounded :bg-indigo-600 :font-bold]} [[1 2][3 4]])")
               second
               v/->value :nextjournal/presented :nextjournal/css-class))))

  (testing "Settings propagation from ns to form"
    (is (= :full
           (-> (eval-test/eval+extract-doc-blocks "(ns nextjournal.clerk.viewer-test.settings {:nextjournal.clerk/width :full}) (nextjournal.clerk/html [:div])")
               (nth 2)
               v/->value :nextjournal/presented :nextjournal/width)))

    (is (= :wide
           (-> (eval-test/eval+extract-doc-blocks "(ns nextjournal.clerk.viewer-test.settings {:nextjournal.clerk/width :full}) (nextjournal.clerk/html {:nextjournal.clerk/width :wide} [:div])")
               (nth 2)
               v/->value :nextjournal/presented :nextjournal/width)))

    (is (= :wide
           (-> (eval-test/eval+extract-doc-blocks "(ns nextjournal.clerk.viewer-test.settings {:nextjournal.clerk/width :full}) ^{:nextjournal.clerk/width :wide} (nextjournal.clerk/html [:div])")
               (nth 2)
               v/->value :nextjournal/presented :nextjournal/width)))

    (is (= :wide
           (-> (eval-test/eval+extract-doc-blocks "(ns nextjournal.clerk.viewer-test.settings {:nextjournal.clerk/width :full}) {:nextjournal.clerk/width :wide} (nextjournal.clerk/html [:div])")
               (nth 2)
               v/->value :nextjournal/presented :nextjournal/width))))

  (testing "Presented doc (with fragments) has unambiguous ids assigned to results"
    (let [ids (->> (eval/eval-string "(nextjournal.clerk/table [[1 2][3 4]])
(nextjournal.clerk/fragment
 5
 (nextjournal.clerk/html [:div 6])
 (nextjournal.clerk/fragment 7 8))")
                   view/doc->viewer v/->value :blocks
                   (tree-seq coll? seq)
                   (filter (every-pred map? :nextjournal/presented))
                   (map (comp :id :nextjournal/render-opts :nextjournal/presented)))]
      (is (= 5 (count ids)))
      (is (every? (every-pred not-empty string?) ids))
      (is (distinct? ids))))

  (testing "Clerk fragments and comments render values from def vars"
    (is (= 3
           (-> (eval-test/eval+extract-doc-blocks "(ns nextjournal.clerk.viewer-test.fragments (:require [nextjournal.clerk :as clerk]))
(clerk/fragment
 1 2 (def x 3))")
               last :nextjournal/value :nextjournal/presented :nextjournal/value)))
    (is (= 3
           (-> (eval-test/eval+extract-doc-blocks "(ns nextjournal.clerk.viewer-test.fragments (:require [nextjournal.clerk :as clerk]))
(clerk/comment
 1 2 (def x 3))")
               last :nextjournal/value :nextjournal/presented :nextjournal/value)))

    (is (= 4
           (-> (eval-test/eval+extract-doc-blocks "(ns nextjournal.clerk.viewer-test.fragments (:require [nextjournal.clerk :as clerk]))
(clerk/comment
 1 2 (clerk/comment 3 (def x 4)))")
               last :nextjournal/value :nextjournal/presented :nextjournal/value)))

    (is (= 4
           (-> (eval-test/eval+extract-doc-blocks "(ns nextjournal.clerk.viewer-test.fragments (:require [nextjournal.clerk :as clerk]))
(clerk/fragment
 1 (clerk/comment 2 (clerk/fragment 3 (def x 4))))")
               last :nextjournal/value :nextjournal/presented :nextjournal/value))))

  (testing "Fragments emit distinct results for all of their (nested) children"
    (is (= 6
           (count
            (->> (eval/eval-string "1\n(nextjournal.clerk/fragment 2 3 (nextjournal.clerk/fragment 4 5))\n6")
                 view/doc->viewer v/->value :blocks
                 (tree-seq coll? seq)
                 (filter (every-pred map? :nextjournal/presented :nextjournal/blob-id)))))))

  (testing "customizing budget, user-facing"
    (is (= 5
           (count
            (->> (eval/eval-string "^{:nextjournal.clerk/budget 5}(reduce (fn [acc _i] (vector acc)) :fin (range 100 0 -1))")
                 view/doc->viewer v/->value :blocks
                 (tree-seq coll? seq)
                 (filter (every-pred map? (comp #{'nextjournal.clerk.render/render-coll} :form :render-fn)))))))

    (is (= 5
           (count
            (->> (eval/eval-string "(nextjournal.clerk/with-viewer {} {:nextjournal.clerk/budget 5} (reduce (fn [acc i] (vector acc)) :fin (range 15 0 -1)))")
                 view/doc->viewer v/->value :blocks
                 (tree-seq coll? seq)
                 (filter (every-pred map? (comp #{'nextjournal.clerk.render/render-coll} :form :render-fn)))))))

    (is (= 101
           (count
            (->> (eval/eval-string "^{:nextjournal.clerk/budget nil}(reduce (fn [acc i] (vector i acc)) :fin (range 101 0 -1))")
                 view/doc->viewer v/->value :blocks
                 (tree-seq coll? seq)
                 (filter (every-pred map? (comp #{'nextjournal.clerk.render/render-coll} :form :render-fn)))))))))

(deftest ->edn
  (testing "normal symbols and keywords"
    (is (= "normal-symbol" (pr-str 'normal-symbol)))
    (is (= ":namespaced/keyword" (pr-str :namespaced/keyword))))

  (testing "unreadable symbols and keywords print as clerk/undreadable-edn"
    (is (= "#clerk/unreadable-edn (keyword \"with spaces\")"
           (pr-str (keyword "with spaces"))))
    (is (= "#clerk/unreadable-edn (keyword \"with ns\" \"and spaces\")"
           (pr-str (keyword "with ns" "and spaces"))))
    (is (= "#clerk/unreadable-edn (symbol \"with spaces\")"
           (pr-str (symbol "with spaces"))))
    (is (= "#clerk/unreadable-edn (symbol \"with ns\" \"and spaces\")"
           (pr-str (symbol "with ns" "and spaces"))))
    (is (= "#clerk/unreadable-edn (symbol \"~\")"
           (pr-str (symbol "~")))))

  (testing "symbols and keywords with two slashes readable by `read-string` but not `tools.reader/read-string` print as #clerk/unreadable-edn"
    (is (= "#clerk/unreadable-edn (symbol \"foo\" \"bar/baz\")"
           (pr-str (read-string "foo/bar/baz"))))
    (is (= "#clerk/unreadable-edn (keyword \"foo\" \"bar/baz\")"
           (pr-str (read-string ":foo/bar/baz")))))

  (testing "splicing reader conditional prints normally (issue #338)"
    (is (= "?@" (pr-str (symbol "?@")))))

  (testing "custom print-method for symbol preserves metadata"
    (is (-> (binding [*print-meta* true]
              (pr-str '[^:foo bar])) read-string first meta :foo))))

(deftest removed-metadata
  (is (= "(do 'this)"
         (-> (eval-test/eval+extract-doc-blocks "(ns test.removed-metadata\n(:require [nextjournal.clerk :as c]))\n\n^::c/no-cache (do 'this)")
             second v/->value))))

(deftest col-viewer-map-args
  (testing "extracts first arg as viewer-opts"
    (is (= [{:foo :bar}]
           (v/->value (v/col {:nextjournal.clerk/width :wide} {:foo :bar})))))

  (testing "doesn't treat plain map as viewer-opts"
    (is (= [{:foo :bar} {:bar :baz}]
           (v/->value (v/col {:foo :bar} {:bar :baz}))))))
