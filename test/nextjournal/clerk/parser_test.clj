(ns nextjournal.clerk.parser-test
  (:require [babashka.fs :as fs]
            [clojure.test :refer :all]
            [matcher-combinators.matchers :as m]
            [matcher-combinators.test :refer [match?]]
            [nextjournal.clerk :as clerk :refer [defcached]]
            [nextjournal.clerk.parser :as parser]))

(deftest read-string-tests
  (testing "read-string should read regex's such that value equalility is preserved"
    (is (= '(fn [x] (clojure.string/split x (clojure.core/re-pattern "/")))
           (parser/read-string "(fn [x] (clojure.string/split x #\"/\"))")))))

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
    (is (match? (m/equals {:blocks [{:type :code, :text "^:nextjournal.clerk/no-cache ^:nextjournal.clerk/toc (ns example-notebook)", :ns? true}
                                    {:type :markdown, :doc {:type :doc :content [{:type :heading}
                                                                                 {:type :heading}
                                                                                 {:type :paragraph}]}}
                                    {:type :code, :text "#{3 1 2}"}
                                    {:type :markdown, :doc {:type :doc :content [{:type :heading}]}}
                                    {:type :code, :text "{2 \"bar\" 1 \"foo\"}"}],
                           :visibility #{:show},
                           :title "📶 Sorting",
                           :toc {:type :toc,
                                 :mode true,
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
















