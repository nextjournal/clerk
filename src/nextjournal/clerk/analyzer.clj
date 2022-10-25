(ns nextjournal.clerk.analyzer
  {:nextjournal.clerk/no-cache true}
  (:refer-clojure :exclude [hash read-string])
  (:require [babashka.fs :as fs]
            [edamame.core :as edamame]
            [clojure.core :as core]
            [clojure.java.io :as io]
            [clojure.set :as set]
            [clojure.string :as str]
            [clojure.tools.analyzer :as ana]
            [clojure.tools.analyzer.ast :as ana-ast]
            [clojure.tools.analyzer.jvm :as ana-jvm]
            [clojure.tools.analyzer.utils :as ana-utils]
            [multihash.core :as multihash]
            [multihash.digest :as digest]
            [nextjournal.clerk.parser :as parser]
            [nextjournal.clerk.classpath :as cp]
            [nextjournal.clerk.config :as config]
            [taoensso.nippy :as nippy]
            [weavejester.dependency :as dep]))

(defn deref? [form]
  (and (seq? form)
       (= (first form) `deref)
       (= 2 (count form))))

#_(deref? '@foo/bar)

(defn no-cache-from-meta [form]
  (when (contains? (meta form) :nextjournal.clerk/no-cache)
    (-> form meta :nextjournal.clerk/no-cache)))

(defn no-cache? [& subjects]
  (or (->> subjects
           (map no-cache-from-meta)
           (filter some?)
           first)
      false))

#_(no-cache? '(rand-int 10))
#_(no-cache? '^:nextjournal.clerk/no-cache (def my-rand (rand-int 10)))
#_(no-cache? '(def ^:nextjournal.clerk/no-cache my-rand (rand-int 10)))
#_(no-cache? '^{:nextjournal.clerk/no-cache false} (def ^:nextjournal.clerk/no-cache my-rand (rand-int 10)))

(defn sha1-base58 [s]
  (-> s digest/sha1 multihash/base58))

#_(sha1-base58 "hello")

(defn class-deps [analyzed]
  (set/union (into #{} (comp (keep :class)
                             (filter class?)
                             (map (comp symbol pr-str))) (ana-ast/nodes analyzed))
             (into #{} (comp (filter (comp #{:const} :op))
                             (filter (comp #{:class} :type))
                             (keep :form)) (ana-ast/nodes analyzed))))

#_(map type (:deps (analyze '(+ 1 2))))

(defn rewrite-defcached [form]
  (if (and (list? form)
           (symbol? (first form))
           (= (resolve 'nextjournal.clerk/defcached)
              (resolve (first form))))
    (conj (rest form) 'def)
    form))

#_(rewrite-defcached '(nextjournal.clerk/defcached foo :bar))

(defn deflike? [form]
  (and (seq? form) (symbol? (first form)) (str/starts-with? (name (first form)) "def")))

#_(deflike? '(defonce foo :bar))
#_(deflike? '(rdef foo :bar))

(defn auto-resolves [ns]
  (as-> (ns-aliases ns) $
    (assoc $ :current (ns-name *ns*))
    (zipmap (keys $)
            (map ns-name (vals $)))))

#_(auto-resolves (find-ns 'nextjournal.clerk.parser))
#_(auto-resolves (find-ns 'cards))

(defn read-string [s]
  (edamame/parse-string s {:all true
                           :readers *data-readers*
                           :read-cond :allow
                           :regex #(list `re-pattern %)
                           :features #{:clj}
                           :auto-resolve (auto-resolves (or *ns* (find-ns 'user)))}))

#_(read-string "(ns rule-30 (:require [nextjournal.clerk.viewer :as v]))")

(defn- analyze-form
  ([form] (analyze-form {} form))
  ([bindings form]
   (binding [config/*in-clerk* true]
     (ana-jvm/analyze form (ana-jvm/empty-env) {:bindings bindings}))))

(defn analyze [form]
  (let [!deps      (atom #{})
        mexpander (fn [form env]
                    (let [f (if (seq? form) (first form) form)
                          v (ana-utils/resolve-sym f env)]
                      (when-let [var? (and (not (-> env :locals (get f)))
                                           (var? v))]
                        (swap! !deps conj v)))
                    (ana-jvm/macroexpand-1 form env))
        analyzed (analyze-form {#'ana/macroexpand-1 mexpander} (rewrite-defcached form))
        nodes (ana-ast/nodes analyzed)
        vars (into #{}
                   (comp (filter (comp #{:def} :op))
                         (keep :var)
                         (map symbol))
                   nodes)
        var (when (and (= 1 (count vars))
                       (deflike? form))
              (first vars))
        def-node (when var
                   (first (filter (comp #{:def} :op) nodes)))
        deref-deps (into #{}
                         (comp (filter (comp #{#'deref} :var :fn))
                               (keep #(-> % :args first))
                               (filter :var)
                               (keep (fn [{:keys [op var]}]
                                       (when-not (= op :the-var)
                                         (list `deref (symbol var))))))
                         nodes)
        deps (set/union (set/difference (into #{} (map symbol) @!deps) vars)
                        deref-deps
                        (class-deps analyzed)
                        (when (var? form) #{(symbol form)}))
        hash-fn (-> form meta :nextjournal.clerk/hash-fn)]
    (cond-> {#_#_:analyzed analyzed
             :form form
             :ns-effect? (some? (some #{'clojure.core/require 'clojure.core/in-ns} deps))
             :freezable? (and (not (some #{'clojure.core/intern} deps))
                              (<= (count vars) 1)
                              (if (seq vars) (= var (first vars)) true))
             :no-cache? (no-cache? form (-> def-node :form second) *ns*)}
      hash-fn (assoc :hash-fn hash-fn)
      (seq deps) (assoc :deps deps)
      (seq deref-deps) (assoc :deref-deps deref-deps)
      (seq vars) (assoc :vars vars)
      var (assoc :var var))))

#_(:vars (analyze '(do (def a 41) (def b (inc a)))))
#_(:vars (analyze '(defrecord Node [v l r])))
#_(analyze '(defn foo [s] (str/includes? (p/parse-string-all s) "hi")))
#_(analyze '(defn segments [s] (let [segments (str/split s)]
                                 (str/join segments))))
#_(analyze '(v/md "It's **markdown**!"))
#_(analyze '(in-ns 'user))
#_(analyze '(do (ns foo)))
#_(analyze '(def my-inc inc))
#_(analyze '(def foo :bar))
#_(analyze '(deref #'foo))
#_(analyze '(defn my-inc-2
              ([] (my-inc-2 0))
              ([x] (inc x))))
#_(analyze '(defonce !state (atom {})))
#_(analyze '(vector :h1 (deref !state)))
#_(analyze '(vector :h1 @!state))
#_(analyze '(do (def foo :bar) (def foo-2 :bar)))
#_(analyze '(do (def foo :bar) :baz))
#_(analyze '(intern *ns* 'foo :bar))
#_(analyze '(import javax.imageio.ImageIO))
#_(analyze '(defmulti foo :bar))
#_(analyze '^{:nextjournal.clerk/hash-fn (fn [_] (clerk/valuehash (slurp "notebooks/hello.clj")))}
           (def contents
             (slurp "notebooks/hello.clj")))

#_(type (first (:deps (analyze 'io.methvin.watcher.hashing.FileHasher))))
#_(analyze 'io.methvin.watcher.hashing.FileHasher/DEFAULT_FILE_HASHER)
#_(analyze '@foo/bar)
#_(analyze '(def nextjournal.clerk.analyzer/foo
              (fn* ([] (nextjournal.clerk.analyzer/foo "s"))
                   ([s] (clojure.string/includes?
                         (rewrite-clj.parser/parse-string-all s) "hi")))))
#_(type (first (:deps (analyze #'analyze-form))))
#_(-> (analyze '(proxy [clojure.lang.ISeq] [])))


(defn- circular-dependency-error? [e]
  (-> e ex-data :reason #{::dep/circular-dependency}))

(defn ->key [{:as _analyzed :keys [vars deps form]}])

(defn- analyze-circular-dependency [state vars form dep {:keys [node dependency]}]
  (let [rec-form (concat '(do) [form (get-in state [:->analysis-info dependency :form])])
        rec-var (symbol (str/join "+" (sort (conj vars dep))))
        var (first vars)] ;; TODO: reduce below
    (-> state
        (update :graph #(-> %
                            (dep/remove-edge dependency node)
                            (dep/depend var rec-var)
                            (dep/depend dep rec-var)))
        (assoc-in [:->analysis-info rec-var :form] rec-form))))

(defn ->ana-keys [{:as _analyzed :keys [form vars]}]
  (if (seq vars) vars [form]))

(defn- analyze-deps [{:as analyzed :keys [form vars]} state dep]
  (try (reduce (fn [state var]
                 (update state :graph #(dep/depend % (if var var form) dep)))
               state
               (->ana-keys analyzed))
       (catch Exception e
         (if (circular-dependency-error? e)
           (analyze-circular-dependency state vars form dep (ex-data e))
           (throw e)))))

(defn make-deps-inherit-no-cache [state {:as analyzed :keys [no-cache? vars deps ns-effect?]}]
  (if no-cache?
    state
    (let [no-cache-deps? (boolean (some (fn [dep] (get-in state [:->analysis-info dep :no-cache?])) deps))]
      (reduce (fn [state k]
                (assoc-in state [:->analysis-info k :no-cache?] no-cache-deps?)) state (->ana-keys analyzed)))))

(defn add-block-id [{:as state :keys [id->count]} {:as block :keys [var form type doc]}]
  (let [id (if var
             var
             (let [hash-fn #(-> % nippy/fast-freeze digest/sha1 multihash/base58)]
               (symbol (str *ns*)
                       (case type
                         :code (str "anon-expr-" (hash-fn (cond-> form (instance? clojure.lang.IObj form) (with-meta {}))))
                         :markdown (str "markdown-" (hash-fn doc))))))
        unique-id (if (id->count id)
                    (symbol (str *ns*) (str (name id) "#" (inc (id->count id))))
                    id)]
    (-> state
        (update :blocks conj (assoc block :id unique-id))
        (update :id->count update id (fnil inc 0)))))

(defn add-block-ids [{:as analyzed-doc :keys [blocks]}]
  (-> (reduce add-block-id (assoc analyzed-doc :blocks [] :id->count {} ) blocks)
      (dissoc :id->count)))

(defn ^:private internal-proxy-name?
  "Returns true if `sym` represents a var name interned by `clojure.core/proxy`."
  [sym]
  (str/includes? (name sym) ".proxy$"))

(defn throw-if-dep-is-missing [doc state analyzed]
  (when-let [missing-dep (and (first (set/difference (into #{}
                                                           (comp (filter #(and (symbol? %)
                                                                               (#{(-> state :ns ns-name name)} (namespace %))))
                                                                 (remove internal-proxy-name?))
                                                           (:deps analyzed))
                                                     (-> state :->analysis-info keys set))))]
    (throw (ex-info (str "The var `#'" missing-dep "` exists at runtime, but Clerk cannot find it in the namespace. Did you remove it?")
                    (merge {:var-name missing-dep} (select-keys analyzed [:form]) (select-keys doc [:file]))))))

(defn analyze-doc
  ([doc]
   (analyze-doc {:doc? true :graph (dep/graph)} doc))
  ([{:as state :keys [doc?]} doc]
   (binding [*ns* *ns*]
     (cond-> (reduce (fn [state i]
                       (let [{:keys [type text loc]} (get-in state [:blocks i])]
                         (if (not= type :code)
                           state
                           (let [form (read-string text)
                                 form+loc (cond-> form
                                            (instance? clojure.lang.IObj form)
                                            (vary-meta merge (cond-> loc
                                                               (:file doc) (assoc :clojure.core/eval-file (str (:file doc))))))
                                 {:as analyzed :keys [vars deps ns-effect?]} (cond-> (analyze form+loc)
                                                                               (:file doc) (assoc :file (:file doc)))
                                 _ (when ns-effect? ;; needs to run before setting doc `:ns` via `*ns*`
                                     (eval form))
                                 state (cond-> (reduce (fn [state ana-key]
                                                         (assoc-in state [:->analysis-info ana-key] analyzed))
                                                       (dissoc state :doc?)
                                                       (->ana-keys analyzed))
                                         doc? (update-in [:blocks i] merge (dissoc analyzed :deps :no-cache? :ns-effect?))
                                         (and doc? (not (contains? state :ns))) (merge (parser/->doc-settings form) {:ns *ns*}))]
                             (when (:ns? state)
                               (throw-if-dep-is-missing doc state analyzed))
                             (if (seq deps)
                               (-> (reduce (partial analyze-deps analyzed) state deps)
                                   (make-deps-inherit-no-cache analyzed))
                               state)))))
                     (cond-> state
                       doc? (merge doc))
                     (-> doc :blocks count range))
       doc? (-> add-block-ids parser/add-block-visibility parser/add-open-graph-metadata)))))

(defn analyze-file
  ([file] (analyze-file {:graph (dep/graph)} file))
  ([state file] (analyze-doc state (parser/parse-file {} file))))

#_(:graph (analyze-file {:graph (dep/graph)} "notebooks/elements.clj"))
#_(analyze-file {:graph (dep/graph)} "notebooks/rule_30.clj")
#_(analyze-file {:graph (dep/graph)} "notebooks/recursive.clj")
#_(analyze-file {:graph (dep/graph)} "notebooks/hello.clj")

(defn unhashed-deps [->analysis-info]
  (set/difference (into #{}
                        (mapcat :deps)
                        (vals ->analysis-info))
                  (-> ->analysis-info keys set)))

#_(unhashed-deps {#'elements/fix-case {:deps #{#'rewrite-clj.node/tag}}})

(defn ns->path [ns]
  (str/replace (namespace-munge ns) "." fs/file-separator))

(defn ns->file [ns]
  (some (fn [dir]
          (some (fn [ext]
                  (let [path (str dir fs/file-separator (ns->path ns) ext)]
                    (when (fs/exists? path)
                      path)))
                [".clj" ".cljc"]))
        (cp/classpath-directories)))

(defn ns->jar [ns]
  (let [path (ns->path ns)]
    (some #(when (or (.getJarEntry % (str path ".clj"))
                     (.getJarEntry % (str path ".cljc")))
             (.getName %))
          (cp/classpath-jarfiles))))

#_(ns->jar (find-ns 'weavejester.dependency))

(defn guard [x f]
  (when (f x) x))

(defn symbol->jar [sym]
  (some-> (if (qualified-symbol? sym)
            (-> sym namespace symbol)
            sym)
          resolve
          .getProtectionDomain
          .getCodeSource
          .getLocation
          (guard #(= "file" (.getProtocol %)))
          .getFile
          (guard #(str/ends-with? % ".jar"))))

#_(symbol->jar 'io.methvin.watcher.PathUtils)
#_(symbol->jar 'io.methvin.watcher.PathUtils/cast)
#_(symbol->jar 'java.net.http.HttpClient/newHttpClient)

(defn find-location [sym]
  (cond
    (deref? sym) (find-location (second sym))
    :else (if-let [ns (and (qualified-symbol? sym) (-> sym namespace symbol find-ns))]
            (or (ns->file ns)
                (ns->jar ns))
            (symbol->jar sym))))

#_(find-location `inc)
#_(find-location #'inc)
#_(find-location `dep/depend)
#_(find-location 'java.util.UUID)
#_(find-location 'java.util.UUID/randomUUID)
#_(find-location 'io.methvin.watcher.PathUtils)
#_(find-location 'io.methvin.watcher.hashing.FileHasher/DEFAULT_FILE_HASHER)
#_(find-location 'String)

(def hash-jar
  (memoize (fn [f]
             {:jar f :hash (sha1-base58 (io/input-stream f))})))

#_(hash-jar (find-location `dep/depend))

(defn build-graph
  "Analyzes the forms in the given file and builds a dependency graph of the vars.

  Recursively decends into dependency vars as well as given they can be found in the classpath.
  "
  [doc]
  (let [{:as graph :keys [->analysis-info]} (analyze-doc doc)]
    (reduce (fn [g [source symbols]]
              (if (or (nil? source)
                      (str/ends-with? source ".jar"))
                (update g :->analysis-info merge (into {} (map (juxt identity (constantly (if source (hash-jar source) {})))) symbols))
                (analyze-file g source)))
            graph
            (group-by find-location (unhashed-deps ->analysis-info)))))


#_(build-graph (parser/parse-clojure-string (slurp "notebooks/hello.clj")))
#_(keys (:->analysis-info (build-graph "notebooks/elements.clj")))
#_(dep/immediate-dependencies (:graph (build-graph "notebooks/elements.clj"))  #'nextjournal.clerk.demo/fix-case)
#_(dep/transitive-dependencies (:graph (build-graph "notebooks/elements.clj"))  #'nextjournal.clerk.demo/fix-case)

#_(keys (:->analysis-info (build-graph "src/nextjournal/clerk/analyzer.clj")))
#_(dep/topo-sort (:graph (build-graph "src/nextjournal/clerk/analyzer.clj")))
#_(dep/immediate-dependencies (:graph (build-graph "src/nextjournal/clerk/analyzer.clj"))  #'nextjournal.clerk.analyzer/long-thing)
#_(dep/transitive-dependencies (:graph (build-graph "src/nextjournal/clerk/analyzer.clj"))  #'nextjournal.clerk.analyzer/long-thing)

(defn hash-codeblock [->hash {:as codeblock :keys [hash form deps vars]}]
  (let [->hash' (if (and (not (ifn? ->hash)) (seq deps))
                  (binding [*out* *err*]
                    (println "->hash must be `ifn?`" {:->hash ->hash :codeblock codeblock})
                    identity)
                  ->hash)
        hashed-deps (into #{} (map ->hash') deps)]
    (sha1-base58 (binding [*print-length* nil]
                   (pr-str (set/union (conj hashed-deps (if form form hash))
                                      vars))))))

#_(nextjournal.clerk/build-static-app! {:paths nextjournal.clerk/clerk-docs})

(defn hash
  ([{:as analyzed-doc :keys [graph]}] (hash analyzed-doc (dep/topo-sort graph)))
  ([{:as analyzed-doc :keys [->analysis-info graph]} deps]
   (update analyzed-doc
           :->hash
           (partial reduce (fn [->hash k]
                             (if-let [codeblock (get ->analysis-info k)]
                               (assoc ->hash k (hash-codeblock ->hash codeblock))
                               ->hash)))
           deps)))

#_(hash (build-graph (parser/parse-clojure-string "^{:nextjournal.clerk/hash-fn (fn [x] \"abc\")}(def contents (slurp \"notebooks/hello.clj\"))")))
#_(hash (build-graph (parser/parse-clojure-string (slurp "notebooks/hello.clj"))))

(defn exceeds-bounded-count-limit? [x]
  (reduce (fn [_ xs]
            (try
              (let [limit config/*bounded-count-limit*]
                (if (and (seqable? xs) (<= limit (bounded-count limit xs)))
                  (reduced true)
                  false))
              (catch Exception _e
                (reduced true))))
          false
          (tree-seq seqable? seq x)))

#_(exceeds-bounded-count-limit? (range config/*bounded-count-limit*))
#_(exceeds-bounded-count-limit? (range (dec config/*bounded-count-limit*)))
#_(exceeds-bounded-count-limit? {:a-range (range)})

(defn valuehash
  ([value] (valuehash :sha512 value))
  ([hash-type value]
   (let [digest-fn (case hash-type
                     :sha1 digest/sha1
                     :sha512 digest/sha2-512)]
     (-> value
         nippy/fast-freeze
         digest-fn
         multihash/base58))))

#_(valuehash (range 100))
#_(valuehash :sha1 (range 100))
#_(valuehash (zipmap (range 100) (range 100)))

(defn ->hash-str
  "Attempts to compute a hash of `value` falling back to a random string."
  [value]
  (or (try (when-not (exceeds-bounded-count-limit? value)
             (valuehash value))
           (catch Exception _))
      (str (gensym))))

#_(->hash-str (range 104))
#_(->hash-str (range))

(defn hash-deref-deps [{:as analyzed-doc :keys [graph ->hash blocks visibility]} {:as cell :keys [deps deref-deps hash-fn var form]}]
  (if (seq deref-deps)
    (let [deref-deps-to-eval (set/difference deref-deps (-> ->hash keys set))
          doc-with-deref-dep-hashes (reduce (fn [state deref-dep]
                                              (assoc-in state [:->hash deref-dep] (->hash-str (eval deref-dep))))
                                            analyzed-doc
                                            deref-deps-to-eval)]
      #_(prn :hash-deref-deps/form form :deref-deps deref-deps-to-eval)
      (hash doc-with-deref-dep-hashes (dep/transitive-dependents-set graph deref-deps-to-eval)))
    analyzed-doc))

#_(nextjournal.clerk/show! "notebooks/hash_fn.clj")

#_(do (swap! hash-fn/!state inc)
      (nextjournal.clerk/recompute!))

#_(deref nextjournal.clerk.webserver/!doc)

#_(nextjournal.clerk/clear-cache!)
