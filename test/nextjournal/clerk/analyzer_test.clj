(ns nextjournal.clerk.analyzer-test
  (:require [babashka.fs :as fs]
            [clojure.test :refer :all]
            [matcher-combinators.matchers :as m]
            [matcher-combinators.test :refer [match?]]
            [nextjournal.clerk :as clerk :refer [defcached]]
            [nextjournal.clerk.analyzer :as ana]
            [nextjournal.clerk.parser :as parser]
            [weavejester.dependency :as dep]))

(defmacro with-ns-binding [ns-sym & body]
  `(binding [*ns* (find-ns ~ns-sym)]
     ~@body))

(deftest ns->path
  (testing "converts dashes to underscores"
    (is (= (str "rewrite_clj" fs/file-separator "parser")
           (ana/ns->path (find-ns 'rewrite-clj.parser))))))

(deftest no-cache?
  (with-ns-binding 'nextjournal.clerk.analyzer-test
    (testing "are variables set to no-cache?"
      (is (not (:no-cache? (ana/analyze '(rand-int 10)))))
      (is (not (:no-cache? (ana/analyze '(def random-thing (rand-int 1000))))))
      (is (not (:no-cache? (ana/analyze '(defn random-thing [] (rand-int 1000))))))
      (is (:no-cache? (ana/analyze '^:nextjournal.clerk/no-cache (rand-int 10))))
      (is (:no-cache? (ana/analyze '^:nextjournal.clerk/no-cache (def random-thing (rand-int 1000)))))
      (is (:no-cache? (ana/analyze '^:nextjournal.clerk/no-cache (defn random-thing [] (rand-int 1000))))))


    (testing "deprecated way to set no-cache"
      (is (:no-cache? (ana/analyze '(def ^:nextjournal.clerk/no-cache random-thing (rand-int 1000)))))
      (is (:no-cache? (ana/analyze '(defn ^:nextjournal.clerk/no-cache random-thing [] (rand-int 1000)))))
      (is (:no-cache? (ana/analyze '(defn ^{:nextjournal.clerk/no-cache true} random-thing [] (rand-int 1000)))))))

  (testing "is evaluating namespace set to no-cache?"
    (is (not (ana/no-cache? '(rand-int 10) (find-ns 'nextjournal.clerk.analyzer-test))))

    (is (nextjournal.clerk.analyzer/no-cache? '(rand-int 10) (find-ns 'nextjournal.clerk.analyzer)))))

(deftest deps
  (is (match? #{'clojure.string/includes?
                'clojure.core/fn
                'clojure.core/defn
                'rewrite-clj.parser/parse-string-all}
              (:deps (ana/analyze '(defn foo
                                   ([] (foo "s"))
                                   ([s] (clojure.string/includes? (rewrite-clj.parser/parse-string-all s) "hi")))))))

  (testing "finds deps inside maps and sets"
    (is (match? '#{nextjournal.clerk.analyzer-test/foo
                   nextjournal.clerk.analyzer-test/bar}
                (with-ns-binding 'nextjournal.clerk.analyzer-test
                  (intern *ns* 'foo :foo)
                  (intern *ns* 'bar :bar)
                  (:deps (ana/analyze '{:k-1 foo :k-2 #{bar}})))))))


(deftest analyze
  (testing "quoted forms aren't confused with variable dependencies"
    (is (match? {:deps #{`inc}}
                (ana/analyze '(do inc))))
    (is (empty? (:deps (ana/analyze '(do 'inc))))))

  (testing "locals that shadow existing vars shouldn't show up in the deps"
    (is (= #{'clojure.core/let} (:deps (ana/analyze '(let [+ 2] +))))))

  (testing "symbol referring to a java class"
    (is (match? {:deps       #{'io.methvin.watcher.PathUtils}}
                (ana/analyze 'io.methvin.watcher.PathUtils))))

  (testing "namespaced symbol referring to a java thing"
    (is (match? {:deps       #{'io.methvin.watcher.hashing.FileHasher}}
                (ana/analyze 'io.methvin.watcher.hashing.FileHasher/DEFAULT_FILE_HASHER))))

  (is (match? {:ns-effect? false
               :vars '#{nextjournal.clerk.analyzer/foo}
               :deps       #{'rewrite-clj.parser/parse-string-all
                             'clojure.core/fn
                             'clojure.core/defn
                             'clojure.string/includes?}}
              (with-ns-binding 'nextjournal.clerk.analyzer
                (ana/analyze '(defn foo [s]
                                (clojure.string/includes? (rewrite-clj.parser/parse-string-all s) "hi"))))))

  (is (match? {:ns-effect?   false
               :vars '#{nextjournal.clerk.analyzer/segments}
               :deps         #{'clojure.string/split
                               'clojure.core/let
                               'clojure.core/defn
                               'clojure.core/fn
                               'clojure.string/join}}
              (with-ns-binding 'nextjournal.clerk.analyzer
                (ana/analyze '(defn segments [s] (let [segments (clojure.string/split s)]
                                                   (clojure.string/join segments)))))))

  (is (match? {:form       '(in-ns 'user)
               :ns-effect? true
               :deps       #{'clojure.core/in-ns}}
              (ana/analyze '(in-ns 'user))))

  (is (match? {:ns-effect? true
               :deps       (m/embeds #{'clojure.core/in-ns})}
              (ana/analyze '(do (ns foo)))))

  (is (match? {:ns-effect? false
               :deps       #{'clojure.core/inc}}
              (ana/analyze '(def my-inc inc))))

  (is (match? {:ns-effect? false
               :vars '#{nextjournal.clerk.analyzer-test/!state}
               :deps       #{'clojure.lang.Var
                             'clojure.core/atom
                             'clojure.core/let
                             'clojure.core/when-not
                             'clojure.core/defonce}}
              (with-ns-binding 'nextjournal.clerk.analyzer-test
                (ana/analyze '(defonce !state (atom {}))))))

  (is (match? {:ns-effect? false
               :vars '#{nextjournal.clerk.analyzer-test/foo nextjournal.clerk.analyzer-test/foo-2}}
              (with-ns-binding 'nextjournal.clerk.analyzer-test
                (ana/analyze '(do (def foo :bar) (def foo-2 :bar))))))

  (testing "dereferenced var isn't detected as a deref dep"
    (with-ns-binding 'nextjournal.clerk.analyzer-test
      (intern *ns* 'foo :bar)
      (is (empty? (-> '(deref #'foo) ana/analyze :deref-deps)))))

  (testing "deref dep inside fn is detected"
    (with-ns-binding 'nextjournal.clerk.analyzer-test
      (intern *ns* 'foo :bar)
      (is (= #{`(deref nextjournal.clerk.analyzer-test/foo)}
             (-> '(fn [] (deref foo)) ana/analyze :deref-deps)))))

  (testing "deref dep is empty when shadowed"
    (with-ns-binding 'nextjournal.clerk.analyzer-test
      (intern *ns* 'foo :bar)
      (is (empty? (-> '(fn [foo] (deref foo)) ana/analyze :deref-deps)))))

  (testing "defcached should be treated like a normal def"
    (with-ns-binding 'nextjournal.clerk.analyzer-test
      (is (= (dissoc (ana/analyze '(def answer (do (Thread/sleep 4200) (inc 41)))) :form)
             (dissoc (ana/analyze '(defcached answer (do (Thread/sleep 4200) (inc 41)))) :form)
             (dissoc (ana/analyze '(clerk/defcached answer (do (Thread/sleep 4200) (inc 41)))) :form)
             (dissoc (ana/analyze '(nextjournal.clerk/defcached answer (do (Thread/sleep 4200) (inc 41)))) :form))))))

(deftest symbol->jar
  (is (ana/symbol->jar 'io.methvin.watcher.PathUtils))
  (is (ana/symbol->jar 'io.methvin.watcher.PathUtils/cast))
  (testing "does not resolve jdk builtins"
    (is (not (ana/symbol->jar 'java.net.http.HttpClient/newHttpClient)))))

(defn analyze-string [s]
  (-> (parser/parse-clojure-string {:doc? true} s)
      ana/analyze-doc))

(deftest analyze-doc
  (is (match? {:graph {:dependencies {'(ns example-notebook) set?}
                       :dependents   map?}
               :blocks [{:type :code
                         :text "^:nextjournal.clerk/no-cache (ns example-notebook)"
                         :form '(ns example-notebook)
                         :ns?  true}
                        {:type :code
                         :text "#{3 1 2}"
                         :form #{1 2 3}}]
               :visibility #{:show}
               :->analysis-info {'(ns example-notebook) {:form '(ns example-notebook),
                                                         :deps set?}
                                 #{1 3 2} {:form '#{1 3 2}}}}
              (analyze-string "^:nextjournal.clerk/no-cache (ns example-notebook)
#{3 1 2}")))

  (testing "preserves *ns*"
    (with-ns-binding 'nextjournal.clerk.analyzer-test
      (is (= (find-ns 'nextjournal.clerk.analyzer-test)
             (do (analyze-string "(ns example-notebook)") *ns*)))))

  (testing "defmulti has no deref deps"
    (is (empty? (-> "(defmulti foo :bar)" analyze-string :blocks first :deref-deps)))))


(deftest no-cache-dep
  (is (match? [{:no-cache? true} {:no-cache? true} {:no-cache? true}]
              (->> "(def ^:nextjournal.clerk/no-cache my-uuid
  (java.util.UUID/randomUUID))
(str my-uuid)
my-uuid"
                   analyze-string
                   ana/analyze-doc
                   :->analysis-info
                   vals))))

(deftest circular-dependency
  (is (match? {:graph {:dependencies {'(ns circular) any?
                                      'circular/b #{'clojure.core/str 'circular/a+circular/b}
                                      'circular/a #{'clojure.core/declare 'clojure.core/str 'circular/a+circular/b}}}
               :->analysis-info {'circular/a any?
                                 'circular/b any?
                                 'circular/a+circular/b {:form '(do (def a (str "boom " b)) (def b (str a " boom")))}}}
              (analyze-string "(ns circular)
(declare a)
(def b (str a \" boom\"))
(def a (str \"boom \" b))"))))
