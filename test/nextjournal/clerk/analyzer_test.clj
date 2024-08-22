(ns nextjournal.clerk.analyzer-test
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [matcher-combinators.matchers :as m]
            [matcher-combinators.test :refer [match?]]
            #_:clj-kondo/ignore
            [nextjournal.clerk :as clerk :refer [defcached]]
            [nextjournal.clerk.analyzer :as ana]
            [nextjournal.clerk.config :as config]
            [nextjournal.clerk.fixtures.dep-a]
            [nextjournal.clerk.fixtures.dep-b]
            [nextjournal.clerk.parser :as parser]
            [weavejester.dependency :as dep])
  (:import (clojure.lang ExceptionInfo)))

(defmacro with-ns-binding [ns-sym & body]
  `(binding [*ns* (find-ns ~ns-sym)]
     ~@body))

(deftest ns->path
  (testing "converts dashes to underscores"
    (is (= (str "rewrite_clj" fs/file-separator "parser")
           (ana/ns->path (find-ns 'rewrite-clj.parser))))))

(deftest ns->file
  (testing "ns arg"
    (is (= (str (fs/file "src" "nextjournal" "clerk" "analyzer.clj")) (ana/ns->file (find-ns 'nextjournal.clerk.analyzer)))))

  (testing "symbol cljc"
    (is (= (str (fs/file "src" "nextjournal" "clerk" "viewer.cljc")) (ana/ns->file 'nextjournal.clerk.viewer)))))

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

(deftest exceeds-bounded-count-limit?
  (is (ana/exceeds-bounded-count-limit? (range config/*bounded-count-limit*)))
  (is (not (ana/exceeds-bounded-count-limit? (range (dec config/*bounded-count-limit*)))))
  (is (ana/exceeds-bounded-count-limit? (map inc (range))))
  (is (ana/exceeds-bounded-count-limit? {:a-range (range)})))

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
                  (:deps (ana/analyze '{:k-1 foo :k-2 #{bar}}))))))

  (testing "deps should all be symbols"
    (is (every? symbol? (:deps (ana/analyze '(.hashCode clojure.lang.Compiler)))))

    (is (every? symbol? (:deps (ana/analyze '(defprotocol MyProtocol
                                               (-check [_])))))))

  (testing "protocol methods are resolved to protocol in deps"
    (is (= '#{nextjournal.clerk.analyzer/BoundedCountCheck}
           (:deps (ana/analyze 'nextjournal.clerk.analyzer/-exceeds-bounded-count-limit?))))))

(deftest read-string-tests
  (testing "read-string should read regex's such that value equalility is preserved"
    (is (= '(fn [x] (clojure.string/split x (clojure.core/re-pattern "/")))
           (ana/read-string "(fn [x] (clojure.string/split x #\"/\"))"))))

  (testing "read-string can handle syntax quote"
    (is (match? '['nextjournal.clerk.analyzer-test/foo 'nextjournal.clerk/foo 'nextjournal.clerk/foo]
                (with-ns-binding 'nextjournal.clerk.analyzer-test
                  (ana/read-string "[`foo `clerk/foo `nextjournal.clerk/foo]"))))))

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

  (ana/analyze '(do (def my-inc inc) (def my-dec dec)))

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
             (dissoc (ana/analyze '(nextjournal.clerk/defcached answer (do (Thread/sleep 4200) (inc 41)))) :form)))))

  (testing "tools.analyzer AssertionError is rethrown as ExceptionInfo (#307)"
    (is (thrown? ExceptionInfo (ana/analyze '(def foo [] :bar))))))

(deftest symbol->jar
  (is (ana/symbol->jar 'io.methvin.watcher.PathUtils))
  (is (ana/symbol->jar 'io.methvin.watcher.PathUtils/cast))
  (testing "does not resolve jdk builtins"
    (is (not (ana/symbol->jar 'java.net.http.HttpClient/newHttpClient)))))

(deftest find-location
  (testing "clojure.core/inc"
    (is (re-find #"clojure-1\..*\.jar" (ana/find-location 'clojure.core/inc))))

  (testing "weavejester.dependency/graph"
    (is (re-find #"dependency-.*\.jar" (ana/find-location 'weavejester.dependency/graph)))))

(defn analyze-string [s]
  (-> (parser/parse-clojure-string {:doc? true} s)
      ana/build-graph))

(deftest hash-test
  (testing "The hash of weavejester/dependency is the same across OSes"
    (is (match?
         {:jar
          #"repository/weavejester/dependency/0.2.1/dependency-0.2.1.jar",
          :hash "5dsZiMRBpbMfWTafMoHEaNdGfEYxpx"}
         (ana/hash-jar (ana/find-location 'weavejester.dependency/graph)))))

  (testing "an edge-case with a particular sorting of the graph nodes"
    (is (-> (analyze-string "
(do
  (declare b)
  (def a 1))

(inc a)") ana/hash)))

  (testing "expressions do not depend on forward declarations"
    (let [ana-1 (-> "(ns nextjournal.clerk.analyzer-test.forward-declarations)

(declare x)
(defn foo [] (inc (x)))
(defn x [] 0)
"
                    analyze-string ana/hash)
          block-3-id (-> ana-1 :blocks (nth 2) :id)
          hash-1 (-> ana-1 :->hash block-3-id)
          ana-2 (-> "(ns nextjournal.clerk.analyzer-test.forward-declarations)

(declare x y)
(defn foo [] (inc (x)))
(defn x [] 0)
"
                    analyze-string ana/hash)
          hash-2 (-> ana-2 :->hash block-3-id)]

      (is hash-1) (is hash-2)
      (is (= hash-1 hash-2))))

  (testing "forms depending on anonymous forms"
    (let [ana-1 (-> "(ns nextjournal.clerk.analyzer-test.dependency-on-anon-forms)
(do
  :something
  (def x 0))

(inc x)
"
                    analyze-string ana/hash)
          block-id (-> ana-1 :blocks second :id)
          hash-1 (-> ana-1 :->hash block-id)
          ana-2 (-> "(ns nextjournal.clerk.analyzer-test.dependency-on-anon-forms)
(do
  :something
  (def x 1))

(inc x)
"
                    analyze-string ana/hash)
          block-id (-> ana-2 :blocks second :id)
          hash-2 (-> ana-2 :->hash block-id)]

      (is hash-1) (is hash-2)
      (is (not= hash-1 hash-2))))

  (testing "redefinitions (and their dependents) are never cached"
    (let [{:keys [->analysis-info]} (analyze-string "(ns nextjournal.clerk.analyzer-test.redefs)
(def a 0)
(def b (inc a))
(def a 1)
")]
      (is (:no-cache? (get ->analysis-info 'nextjournal.clerk.analyzer-test.redefs/a)))
      (is (:no-cache? (get ->analysis-info 'nextjournal.clerk.analyzer-test.redefs/b)))
      (is (:no-cache? (get ->analysis-info 'nextjournal.clerk.analyzer-test.redefs/a#2)))))

  (testing "declared vars don't count as redefinition"
    (let [{:keys [->analysis-info]} (analyze-string "(ns nextjournal.clerk.analyzer-test.declares)
(declare a)
(defn b [] (inc a))
(def a 1)
")]
      (is (not (:no-cache? (get ->analysis-info 'nextjournal.clerk.analyzer-test.redefs/a))))
      (is (not (:no-cache? (get ->analysis-info 'nextjournal.clerk.analyzer-test.redefs/b))))
      (is (not (:no-cache? (get ->analysis-info 'nextjournal.clerk.analyzer-test.redefs/a#2)))))))

(deftest analyze-doc
  (testing "reading a bad block shows block and file info in raised exception"
    (is (thrown-match? ExceptionInfo
                       {:block {:type :code :text "##boom"}
                        :file any?}
                       (-> (parser/parse-clojure-string {:doc? true} "(ns some-ns (:require []))")
                           (update-in [:blocks 0 :text] (constantly "##boom"))
                           ana/analyze-doc))))
  (is (match? #{{}
                {:form '(ns example-notebook),
                 :deps set?}
                {:form '#{1 3 2}}
                {:jar string? :hash string?}}
              (-> "^:nextjournal.clerk/no-cache (ns example-notebook)
#{3 1 2}"
                  analyze-string :->analysis-info vals set)))

  (testing "preserves *ns*"
    (with-ns-binding 'nextjournal.clerk.analyzer-test
      (is (= (find-ns 'nextjournal.clerk.analyzer-test)
             (do (analyze-string ";; boo\n\n (ns example-notebook)") *ns*)))))

  (testing "has empty analysis info for JDK built-in"
    (is (= {} (get-in (analyze-string "(do (Thread/sleep 1) 42)") [:->analysis-info 'java.lang.Thread]))))

  (testing "defmulti has no deref deps"
    (is (empty? (-> "(defmulti foo :bar)" analyze-string :blocks first :deref-deps))))

  (testing "can analyze plain var reference (issue #289)"
    (ana/build-graph (analyze-string "clojure.core/inc")))

  (testing "removes block with reader conditional without clj branch (issue #332)"
    (is (empty? (:blocks (analyze-string "#?(:cljs (inc 41))")))))

  (testing "can handle splicing reader-conditional (issue #338)"
    (is (match? [{:form '(do) :text "(do #?@(:cljs []))"}]
                (-> "(do #?@(:cljs []))" analyze-string :blocks)))))

(deftest analyze-file
  (testing "should analyze depedencies"
    (is (-> (ana/analyze-file "src/nextjournal/clerk/classpath.clj") :->analysis-info not-empty))))

(deftest add-block-ids
  (testing "assigns block ids"
    (is (= '[foo/anon-expr-5drCkCGrPisMxHpJVeyoWwviSU3pfm
             foo/bar
             foo/bar#2
             foo/anon-expr-5dsbEK7B7yDZqzyteqsY2ndKVE9p3G
             foo/anon-expr-5dsbEK7B7yDZqzyteqsY2ndKVE9p3G#2]
           (->> "(ns foo {:nextjournal.clerk/visibility {:code :fold}}) (def bar :baz) (def bar :baz) (rand-int 42) (rand-int 42)"
                analyze-string :blocks (mapv :id))))))

(deftest no-cache-dep
  (is (match? [{:no-cache? true} {:no-cache? true} {:no-cache? true}]
              (let [{:keys [blocks ->analysis-info]} (analyze-string "(def ^:nextjournal.clerk/no-cache my-uuid
  (java.util.UUID/randomUUID))
(str my-uuid)
my-uuid")]
                (mapv (comp ->analysis-info :id) blocks)))))

(deftest can-analyze-proxy-macro
  (is (analyze-string "(ns proxy-example-notebook) (proxy [clojure.lang.ISeq][] (seq [] '(this is a test seq)))")))

(deftest circular-dependency
  (is (match? {:graph {:dependencies {'circular/b #{'clojure.core/str
                                                    (symbol "circular/a+circular/b")}
                                      'circular/a #{#_'clojure.core/declare 'clojure.core/str (symbol "circular/a+circular/b")}}}
               :->analysis-info {'circular/a any?
                                 'circular/b any?
                                 (symbol "circular/a+circular/b") {:form '(do (def b (str a " boom"))   (def a (str "boom " b)))}}}
              (analyze-string "(ns circular)
(declare a)
(def b (str a \" boom\"))
(def a (str \"boom \" b))"))))


(deftest build-graph
  (testing "should have no unhashed deps for clojure.set"
    (is (empty? (-> "(ns foo (:require [clojure.set :as set])) (set/union #{1} #{2})" analyze-string :->analysis-info ana/unhashed-deps))))

  (testing "should have analysis info and no unhashed deps for `dep/graph`"
    (prn :find-location (ana/find-location 'weavejester.dependency/graph))
    (let [{:keys [->analysis-info]} (analyze-string "(ns foo (:require [weavejester.dependency :as dep])) (dep/graph)")]
      (is (empty? (ana/unhashed-deps ->analysis-info)))
      (is (match? {:jar string?} (->analysis-info 'weavejester.dependency/graph)))))

  (testing "should establish dependencies across files"
    (let [{:keys [graph]} (analyze-string (slurp "src/nextjournal/clerk.clj"))]
      (is (dep/depends? graph 'nextjournal.clerk/show! 'nextjournal.clerk.analyzer/hash)))))

(deftest graph-nodes-with-anonymous-ids
  (testing "nodes with \"anonymous ids\" from dependencies in foreign files respect graph dependencies"

    (def analyzed (analyze-string "(ns nextjournal.clerk.analyzer-test.graph-nodes
(:require [nextjournal.clerk.fixtures.dep-b :as dep-b]))
(def some-dependent-var (dep-b/thing))"))

    (is (dep/depends? (:graph analyzed)
                      'nextjournal.clerk.analyzer-test.graph-nodes/some-dependent-var
                      'nextjournal.clerk.git/read-git-attrs))
    (is (not (contains? (dep/nodes (:graph analyzed))
                        'nextjournal.clerk.fixtures.dep-a/some-function-with-defs-inside)))))

(deftest ->hash
  (testing "notices change in depedency namespace"
    (let [test-var 'nextjournal.clerk.fixtures.my-test-ns/hello
          test-string "(ns test (:require [nextjournal.clerk.fixtures.my-test-ns :as my-test-ns])) (str my-test-ns/hello)"
          spit-with-value #(spit (format "test%s%s.clj" fs/file-separator (str/replace (namespace-munge (namespace test-var)) "." fs/file-separator ))
                                 (format "(ns nextjournal.clerk.fixtures.my-test-ns) (def hello %s)" %))
          _ (spit-with-value :hello)
          analyzed-before (ana/hash (analyze-string test-string))
          _ (spit-with-value :world)
          analyzed-after (ana/hash (analyze-string test-string))]
      (is (not= (get-in analyzed-before [:->hash test-var])
                (get-in analyzed-after [:->hash test-var]))))))

(deftest hash-deref-deps
  (testing "transitive dep gets new hash"
    (let [analyzed-doc (-> (pr-str '(ns nextjournal.clerk.test.deref-dep)
                                   '(defonce !state (atom 42))
                                   '(def foo @!state)
                                   '(def foo+1 (inc foo))
                                   '(def foo+2 (inc foo+1)))
                           analyze-string
                           ana/hash)
          static-hash (get-in analyzed-doc [:->hash 'nextjournal.clerk.test.deref-dep/foo+2])
          _ (intern 'nextjournal.clerk.test.deref-dep '!state (atom 0))
          block-with-deref-dep (first (->> analyzed-doc :blocks (filter :deref-deps)))
          runtime-doc (ana/hash-deref-deps analyzed-doc block-with-deref-dep)
          runtime-hash (get-in runtime-doc [:->hash 'nextjournal.clerk.test.deref-dep/foo+2])]
      (is (match? {:deref-deps #{`(deref nextjournal.clerk.test.deref-dep/!state)}} block-with-deref-dep))
      (is (not= static-hash runtime-hash)))))
