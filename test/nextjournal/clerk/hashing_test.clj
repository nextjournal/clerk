(ns nextjournal.clerk.hashing-test
  (:require [clojure.test :refer :all]
            [matcher-combinators.test :refer [match?]]
            [matcher-combinators.matchers :as m]
            [nextjournal.clerk.hashing :as h]
            [weavejester.dependency :as dep]))

(defmacro eval-in-ns [ns & body]
  `(let [current-ns# *ns*]
    (in-ns ~ns)
    ~@body
    (in-ns (.name current-ns#))))

(deftest no-cache?
  (testing "are variables set to no-cache?"
    (is (not (h/no-cache? '(rand-int 10))))
    (is (not (h/no-cache? '(def random-thing (rand-int 1000)))))
    (is (not (h/no-cache? '(defn random-thing [] (rand-int 1000)))))
    (is (h/no-cache? '(def ^:nextjournal.clerk/no-cache random-thing (rand-int 1000))))
    (is (h/no-cache? '(defn ^:nextjournal.clerk/no-cache random-thing [] (rand-int 1000))))
    (is (h/no-cache? '(defn ^{:nextjournal.clerk/no-cache true} random-thing [] (rand-int 1000))))
    (is (not (h/no-cache? '[defn ^:nextjournal.clerk/no-cache trick [] 1]))))

  (testing "is evaluating namespace set to no-cache?"
    (is (not (h/no-cache? '(rand-int 10))))

    (eval-in-ns 'nextjournal.clerk.hashing
      (is (nextjournal.clerk.hashing/no-cache? '(rand-int 10))))))

(deftest var-dependencies
 (is (= #{#'clojure.string/includes?
          #'rewrite-clj.parser/parse-string-all
          #'clojure.core/defn}
        (h/var-dependencies '(defn foo
                               ([] (foo "s"))
                               ([s] (clojure.string/includes? (rewrite-clj.parser/parse-string-all s) "hi")))))))

(defn- var-named? [expected-var-name]
  (fn [actual] (= expected-var-name (-> actual symbol name))))

(deftest analyze
  (is (match? {:form       '(let [x 2] x)
               :ns-effect? false}
              (h/analyze '(let [x 2] x))))

  (is (match? {:form       '(defn foo [s]
                              (clojure.string/includes? (rewrite-clj.parser/parse-string-all s) "hi"))
               :ns-effect? false
               :deps       #{#'rewrite-clj.parser/parse-string-all
                             #'clojure.string/includes?
                             (var-named? "foo")}}
              (h/analyze '(defn foo [s]
                            (clojure.string/includes? (rewrite-clj.parser/parse-string-all s) "hi")))))

  (is (match? {:form         '(defn segments [s]
                                (let [segments (clojure.string/split s)]
                                  (clojure.string/join segments)))
               :ns-effect?   false
               :deps         #{(var-named? "segments")
                               #'clojure.string/split
                               #'clojure.string/join}}
              (h/analyze '(defn segments [s] (let [segments (clojure.string/split s)]
                                               (clojure.string/join segments))))))

  (is (match? {:form       '(in-ns 'user)
               :ns-effect? true
               :deps       #{#'clojure.core/in-ns}}
              (h/analyze '(in-ns 'user))))

  (is (match? {:form       '(do (ns foo))
               :ns-effect? true
               :deps       #{#'clojure.core/conj
                             #'clojure.core/*loaded-libs*
                             #'clojure.core/deref
                             #'clojure.core/in-ns
                             (var-named? "foo")
                             #'clojure.core/refer
                             #'clojure.core/commute}}
              (h/analyze '(do (ns foo)))))

  (is (match? {:form       '(def my-inc inc)
               :ns-effect? false
               :deps       #{#'clojure.core/inc (var-named? "my-inc")}}
              (h/analyze '(def my-inc inc)))))

(deftest analyze-file
  (is (match? (m/equals
                {:graph {:dependencies {'(ns example-notebook) set?}
                         :dependents   map?}
                 :doc {:doc [{:type :code
                              :text "^:nextjournal.clerk/no-cache\n(ns example-notebook)"
                              :ns?  true}
                             {:type :markdown
                              :doc  {:type    :doc
                                     :content [{:type :heading
                                                :content [{:type :text, :text "📶 Sorting"}]
                                                :heading-level 1}]
                                     :toc     {:type :toc
                                               :content
                                               [{:level 1,
                                                 :type :toc
                                                 :title "📶 Sorting"
                                                 :node
                                                 {:type :heading
                                                  :content [{:type :text, :text "📶 Sorting"}]
                                                  :heading-level 1}
                                                 :path [:content 0]}]}}}
                             {:type :code
                              :text "#{3 1 2}"}
                             {:type :code
                              :text "(comment (nextjournal.clerk/show! \"notebooks/sorting.clj\"))"}]
                       :visibility #{:show}}
                 :var->hash {'(ns example-notebook) {:file "resources/tests/example_notebook.clj",
                                                     :form '(ns example-notebook),
                                                     :deps set?}
                             #{1 3 2} {:file "resources/tests/example_notebook.clj",
                                       :form '#{1 3 2},
                                       :deps nil},
                             '(comment (nextjournal.clerk/show! "notebooks/sorting.clj")) {:file "resources/tests/example_notebook.clj",
                                                                                           :form '(comment (nextjournal.clerk/show! "notebooks/sorting.clj")),
                                                                                           :deps nil}}})
                        (h/analyze-file {:markdown? true}
                                        {:graph (dep/graph)}
                                        "resources/tests/example_notebook.clj"))))
