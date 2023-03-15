(ns nextjournal.clerk.parser-test
  (:require [clojure.test :refer [deftest is testing]]
            [matcher-combinators.matchers :as m]
            [matcher-combinators.test :refer [match?]]
            [nextjournal.clerk.analyzer-test :refer [analyze-string]]
            [nextjournal.clerk.parser :as parser]))

(defmacro with-ns-binding [ns-sym & body]
  `(binding [*ns* (find-ns ~ns-sym)]
     ~@body))

(def notebook "^:nextjournal.clerk/no-cache ^:nextjournal.clerk/toc (ns example-notebook)

;; # 📶 Sorting

;; ## Sorting Sets
;; The following set should be sorted upon description

#{3 1 2}

;; ## Sorting Maps

{2 \"bar\" 1 \"foo\"}
")

(deftest parse-clojure-string
  (testing "is returning blocks with types and markdown structure attached"
    (is (match? (m/equals {:blocks [{:type :code, :text "^:nextjournal.clerk/no-cache ^:nextjournal.clerk/toc (ns example-notebook)"}
                                    {:type :markdown, :doc {:type :doc :content [{:type :heading}
                                                                                 {:type :heading}
                                                                                 {:type :paragraph}]}}
                                    {:type :code, :text "#{3 1 2}"}
                                    {:type :markdown, :doc {:type :doc :content [{:type :heading}]}}
                                    {:type :code, :text "{2 \"bar\" 1 \"foo\"}"},]
                           :title "📶 Sorting",
                           :footnotes []
                           :toc {:type :toc,
                                 :children [{:type :toc :children [{:type :toc}
                                                                   {:type :toc}]}]}})
                (parser/parse-clojure-string {:doc? true} notebook)))))

(deftest parse-inline-comments
  (is (match? {:blocks [{:doc {:content [{:content [{:text "text before"}]}]}}
                        {:text "'some-token ;; with inline comment" :type :code}
                        {:doc {:content [{:content [{:text "text after"}]}]}}]}
              (parser/parse-clojure-string {:doc? true}
                                           ";; text before
                                      'some-token ;; with inline comment
                                      ;; text after
                                      "))))

(deftest parse-markdown-string
  (is (match? {:title "Title"
               :blocks [{:doc {:content [{:type :heading :content [{:text "Title"}]}]}}
                        {:text "'code" :type :code}
                        {:doc {:content [{:content [{:text "par one"}] :type :paragraph}
                                         {:content [{:text "par two"}] :type :paragraph}]}}]}
              (parser/parse-markdown-string {:doc? true}
                                            "# Title
```
'code
```
par one

par two"))))


(deftest ->doc-settings
  (testing "supports legacy notation for toc"
    (is (:toc-visibility (parser/->doc-settings '^{:nextjournal.clerk/toc true} (ns foo)))))

  (testing "supports setting toc using ns metadata"
    (is (:toc-visibility (parser/->doc-settings '(ns foo {:nextjournal.clerk/toc true}))))
    (is (:toc-visibility (parser/->doc-settings '(ns foo "my foo ns docstring" {:nextjournal.clerk/toc true}))))
    (is (:toc-visibility (parser/->doc-settings '(ns ^:nextjournal.clerk/toc foo)))))

  (testing "sets toc visibility on doc"
    (is (:toc-visibility (analyze-string "(ns foo {:nextjournal.clerk/toc true})")))))


(deftest add-block-visbility
  (testing "assigns doc visibility from ns metadata"
    (is (= [{:code :fold, :result :hide} {:code :fold, :result :show}]
           (->> "(ns foo {:nextjournal.clerk/visibility {:code :fold}}) (rand-int 42)" analyze-string :blocks (mapv :visibility)))))

  (testing "assigns doc visibility from top-level visbility map marker"
    (is (= [{:code :hide, :result :hide} {:code :fold, :result :show}]
           (->> "{:nextjournal.clerk/visibility {:code :fold}} (rand-int 42)" analyze-string :blocks (mapv :visibility)))))

  (testing "can change visibility halfway"
    (is (= [{:code :show, :result :show} {:code :hide, :result :hide} {:code :fold, :result :hide}]
           (->> "(rand-int 42) {:nextjournal.clerk/visibility {:code :fold :result :hide}} (rand-int 42)" analyze-string :blocks (mapv :visibility))))))

(deftest add-open-graph-metadata
  (testing "OG metadata should be inferred, but customizable via ns map"
    (is (match? {:title "OG Title"}
                (-> ";; # Doc Title\n(ns my.ns1 {:nextjournal.clerk/open-graph {:title \"OG Title\"}})"
                    analyze-string
                    :open-graph)))

    (is (match? {:title "Doc Title" :description "First paragraph with soft breaks." :url "https://ogp.me"}
                (-> ";; # Doc Title\n(ns my.ns2 {:nextjournal.clerk/open-graph {:url \"https://ogp.me\"}})\n;; ---\n;; First paragraph with soft\n;; breaks."
                    analyze-string
                    :open-graph)))))

(def clerk-ns-alias {'clerk 'nextjournal.clerk
                     'c 'nextjournal.clerk})

(deftest remove-metadata-annotations

  (is (= (parser/text-with-clerk-metadata-removed "^:nextjournal.clerk/no-cache\n(do effect)" clerk-ns-alias)
         "(do effect)"))

  (is (= (parser/text-with-clerk-metadata-removed "^{:nextjournal.clerk/visibility {:code :hide} :some-meta 123}\n^:keep-me\n(view this)" clerk-ns-alias)
         "^{:some-meta 123}\n^:keep-me\n(view this)"))

  (testing "with alias resolution"
    (is (= (parser/text-with-clerk-metadata-removed "^:keep-me\n  ^{::clerk/visibility {:code :hide}}  \n(view that)" clerk-ns-alias)
           "^:keep-me\n  (view that)"))

    (is (= (parser/text-with-clerk-metadata-removed "^:keep-me\n  ^{::c/visibility {:code :hide}}  \n(view that)" clerk-ns-alias)
           "^:keep-me\n  (view that)"))

    (is (= (parser/text-with-clerk-metadata-removed "^::c/no-cache (do 'this)" clerk-ns-alias)
           "(do 'this)")))

  (testing "user metadata should be preserved"
    (is (= (parser/text-with-clerk-metadata-removed "^:should\n  (do nothing)" clerk-ns-alias)
           "^:should\n  (do nothing)"))

    (is (= (parser/text-with-clerk-metadata-removed "^also (do nothing)" clerk-ns-alias)
           "^also (do nothing)"))

    (is (= (parser/text-with-clerk-metadata-removed "^{:this \"as well should\"}\n(do nothing)" clerk-ns-alias)
           "^{:this \"as well should\"}\n(do nothing)")))

  (testing "should preserve comments"
    (is (= (parser/text-with-clerk-metadata-removed "^:nextjournal.clerk/no-cache [] ;; keep me" clerk-ns-alias)
           "[] ;; keep me"))
    (is (= (parser/text-with-clerk-metadata-removed "^:private [] ;; keep me" clerk-ns-alias)
           "^:private [] ;; keep me"))

    (is (= ";; keep me\n[]"
           (parser/text-with-clerk-metadata-removed "^::clerk/no-cache\n;; keep me\n[]" clerk-ns-alias))))

  (testing "should preserve unevals"
    (is (= "#_ keep-me []"
           (parser/text-with-clerk-metadata-removed "^::clerk/no-cache #_ keep-me []" clerk-ns-alias))))

  (testing "meta on vars"
    (is (= "(defonce ^:private my-var (atom nil))"
           (parser/text-with-clerk-metadata-removed "^::clerk/sync (defonce ^:private ^::clerk/no-cache my-var (atom nil))" clerk-ns-alias))))

  (testing "unreadable forms"
    (is (= (parser/text-with-clerk-metadata-removed "^{:un :balanced :map} (do nothing)" clerk-ns-alias)
           "^{:un :balanced :map} (do nothing)"))))
