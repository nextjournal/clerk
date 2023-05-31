(ns nextjournal.clerk.analyzer
  {:nextjournal.clerk/no-cache true}
  (:refer-clojure :exclude [hash read-string])
  (:require [babashka.fs :as fs]
            [edamame.core :as edamame]
            [clojure.core :as core]
            [clojure.java.io :as io]
            [clojure.set :as set]
            [clojure.string :as str]
            [clojure.tools.reader :as tools.reader]
            [clojure.tools.analyzer :as ana]
            [clojure.tools.analyzer.ast :as ana-ast]
            [clojure.tools.analyzer.jvm :as ana-jvm]
            [clojure.tools.analyzer.utils :as ana-utils]
            [clojure.walk :as walk]
            [multiformats.base.b58 :as b58]
            [multiformats.hash :as hash]
            [nextjournal.clerk.parser :as parser]
            [nextjournal.clerk.classpath :as cp]
            [nextjournal.clerk.config :as config]
            [taoensso.nippy :as nippy]
            [weavejester.dependency :as dep]))

(set! *warn-on-reflection* true)

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
  (->> s hash/sha1 hash/encode b58/format-btc))

(defn sha2-base58 [s]
  (->> s hash/sha2-512 hash/encode b58/format-btc))

#_(sha1-base58 "hello")

(defn ^:private ensure-symbol [class-or-sym]
  (cond
    (symbol? class-or-sym) class-or-sym
    (class? class-or-sym) (symbol (pr-str class-or-sym))
    :else (throw (ex-info "not a symbol or a class" {:class-or-sym class-or-sym} (IllegalArgumentException.)))))

(defn class-deps [analyzed]
  (set/union (into #{}
                   (comp (keep :class)
                         (filter class?)
                         (map ensure-symbol))
                   (ana-ast/nodes analyzed))
             (into #{}
                   (comp (filter (comp #{:const} :op))
                         (filter (comp #{:class} :type))
                         (keep :form)
                         (map ensure-symbol))
                   (ana-ast/nodes analyzed))))

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
                           :syntax-quote {:resolve-symbol tools.reader/resolve-symbol}
                           :readers *data-readers*
                           :read-cond :allow
                           :regex #(list `re-pattern %)
                           :features #{:clj}
                           :auto-resolve (auto-resolves (or *ns* (find-ns 'user)))}))

#_(read-string "(ns rule-30 (:require [nextjournal.clerk.viewer :as v]))")

(defn unresolvable-symbol-handler [ns sym ast-node]
  ast-node)

(defn wrong-tag-handler [tag ast-node]
  ast-node)

(def analyzer-passes-opts
  (assoc ana-jvm/default-passes-opts
         :validate/wrong-tag-handler wrong-tag-handler
         :validate/unresolvable-symbol-handler unresolvable-symbol-handler))

(defn form->ex-data
  "Returns ex-data map with the form and its location info from metadata."
  [form]
  (merge (select-keys (meta form) [:line :col :clojure.core/eval-file])
         {:form form}))

(defn- analyze-form
  ([form] (analyze-form {} form))
  ([bindings form]
   (binding [config/*in-clerk* true]
     (try
       (let [old-deftype-hack ana-jvm/-deftype]
         ;; NOTE: workaround for tools.analyzer `-deftype` + `eval` HACK, which redefines classes which doesn't work well with instance? checks
         (with-redefs [ana-jvm/-deftype (fn [name class-name args interfaces]
                                          (when-not (resolve class-name)
                                            (old-deftype-hack name class-name args interfaces)))]
           (ana-jvm/analyze form (ana-jvm/empty-env) {:bindings bindings
                                                      :passes-opts analyzer-passes-opts})))
       (catch java.lang.AssertionError e
         (throw (ex-info "Failed to analyze form"
                         (form->ex-data form)
                         e)))))))

(defn analyze [form]
  (let [!deps      (atom #{})
        mexpander (fn [form env]
                    (let [f (if (seq? form) (first form) form)
                          v (ana-utils/resolve-sym f env)]
                      (when (and (not (-> env :locals (get f))) (var? v))
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
#_(analyze '(declare a))
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


(defn ->key [{:as codeblock :keys [var id]}]
  (if var var id))

(defn ->ana-keys [{:as analyzed :keys [form vars id]}]
  (if (seq vars) vars [(->key analyzed)]))

#_(->> (nextjournal.clerk.eval/eval-string "(rand-int 100) (rand-int 100) (rand-int 100)") :blocks (mapv #(-> % :result :nextjournal/value)))

(defn- analyze-deps [{:as analyzed :keys [form vars]} state dep]
  (try (reduce (fn [state _var] ;; TODO: check if `_var` needs to be used
                 (update state :graph #(dep/depend % (->key analyzed) dep)))
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

(defn get-block-id [!id->count {:as block :keys [var form type doc]}]
  (let [id->count @!id->count
        id (if var
             var
             (let [hash-fn #(-> % nippy/fast-freeze sha1-base58)]
               (symbol (str *ns*)
                       (case type
                         :code (str "anon-expr-" (hash-fn (cond-> form (instance? clojure.lang.IObj form) (with-meta {}))))
                         :markdown (str "markdown-" (hash-fn doc))))))]
    (swap! !id->count update id (fnil inc 0))
    (if (id->count id)
      (symbol (str *ns*) (str (name id) "#" (inc (id->count id))))
      id)))

(defn ^:private internal-proxy-name?
  "Returns true if `sym` represents a var name interned by `clojure.core/proxy`."
  [sym]
  (str/includes? (name sym) ".proxy$"))

(defn throw-if-dep-is-missing [{:keys [blocks ns error-on-missing-vars ->analysis-info file]}]
  (when (= :on error-on-missing-vars)
    (let [block-ids (into #{} (keep :id) blocks)
          ;; only take current blocks into account
          current-analyis (into {} (filter (comp block-ids :id val) ->analysis-info))
          defined (set/union (-> current-analyis keys set)
                             (into #{} (mapcat (comp :nextjournal/interned :result)) blocks))]
      (doseq [{:keys [form deps]} (vals current-analyis)]
        (when (seq deps)
          (when-some [missing-dep (first (set/difference (into #{}
                                                               (comp (filter #(and (symbol? %) (= (-> ns ns-name name) (namespace %))))
                                                                     (remove internal-proxy-name?))
                                                               deps)
                                                         defined))]
            (throw (ex-info (str "The var `#'" missing-dep "` is being referenced, but Clerk can't find it in the namespace's source code. Did you remove it? This validation can fail when the namespace is mutated programmatically (e.g. using `clojure.core/intern` or side-effecting macros). You can turn off this check by adding `{:nextjournal.clerk/error-on-missing-vars :off}` to the namespace metadata.")
                            {:var-name missing-dep :form form :file file #_#_:defined defined }))))))))

(defn filter-code-blocks-without-form [doc]
  (update doc :blocks #(filterv (some-fn :form (complement parser/code?)) %)))

(defn ns-resolver [notebook-ns]
  (if notebook-ns
    (into {} (map (juxt key (comp ns-name val))) (ns-aliases notebook-ns))
    identity))
#_ (ns-resolver *ns*)

(defn analyze-doc
  ([doc]
   (analyze-doc {:doc? true :graph (dep/graph)} doc))
  ([{:as state :keys [doc?]} doc]
   (binding [*ns* *ns*]
     (let [!id->count (atom {})]
       (cond-> (reduce (fn [{:as state notebook-ns :ns} i]
                         (let [{:as block :keys [type text loc]} (get-in doc [:blocks i])]
                           (if (not= type :code)
                             (assoc-in state [:blocks i :id] (get-block-id !id->count block))
                             (let [form (try (read-string text)
                                             (catch Exception e
                                               (throw (ex-info (str "Clerk analysis failed reading block: "
                                                                    (ex-message e))
                                                               {:block block
                                                                :file (:file doc)}
                                                               e))))
                                   form+loc (cond-> form
                                              (instance? clojure.lang.IObj form)
                                              (vary-meta merge (cond-> loc
                                                                 (:file doc) (assoc :clojure.core/eval-file (str (:file doc))))))
                                   {:as analyzed :keys [deps ns-effect?]} (cond-> (analyze form+loc)
                                                                            (:file doc) (assoc :file (:file doc)))
                                   _ (when ns-effect? ;; needs to run before setting doc `:ns` via `*ns*`
                                       (eval form))
                                   block-id (get-block-id !id->count (merge analyzed block))
                                   analyzed (assoc analyzed :id block-id)
                                   state (cond-> (reduce (fn [state ana-key]
                                                           (assoc-in state [:->analysis-info ana-key] analyzed))
                                                         (dissoc state :doc?)
                                                         (->ana-keys analyzed))
                                           doc? (update-in [:blocks i] merge (dissoc analyzed :deps :no-cache? :ns-effect?))
                                           doc? (assoc-in [:blocks i :text-without-meta]
                                                          (parser/text-with-clerk-metadata-removed text (ns-resolver notebook-ns)))
                                           (and doc? (not (contains? state :ns))) (merge (parser/->doc-settings form) {:ns *ns*}))]
                               (if (and (:graph state) (seq deps))
                                 (-> (reduce (partial analyze-deps analyzed) state deps)
                                     (make-deps-inherit-no-cache analyzed))
                                 state)))))
                       (cond-> state
                         doc? (merge doc))
                       (-> doc :blocks count range))
         doc? (-> parser/add-block-settings
                  parser/add-open-graph-metadata
                  filter-code-blocks-without-form))))))

#_(let [parsed (nextjournal.clerk.parser/parse-clojure-string "clojure.core/dec")]
    (build-graph (analyze-doc parsed)))

(defonce !file->analysis-cache
  (atom {}))

#_(reset! !file->analysis-cache {})

(defn analyze-file
  ([file]
   (let [current-file-sha (sha1-base58 (fs/read-all-bytes file))]
            (or (when-let [{:as cached-analysis :keys [file-sha]} (@!file->analysis-cache file)]
                  (when (= file-sha current-file-sha)
                    cached-analysis))
                (let [analysis (analyze-doc {:file-sha current-file-sha} (parser/parse-file {} file))]
                  (swap! !file->analysis-cache assoc file analysis)
                  analysis))))
  ([state file]
   (analyze-doc state (parser/parse-file {} file))))

#_(:graph (analyze-file {:graph (dep/graph)} "notebooks/elements.clj"))
#_(analyze-file {:graph (dep/graph)} "notebooks/rule_30.clj")
#_(analyze-file {:graph (dep/graph)} "notebooks/recursive.clj")
#_(analyze-file {:graph (dep/graph)} "notebooks/hello.clj")

(defn unhashed-deps [->analysis-info]
  (remove deref? (set/difference (into #{}
                                       (mapcat :deps)
                                       (vals ->analysis-info))
                                 (-> ->analysis-info keys set))))

#_(unhashed-deps {#'elements/fix-case {:deps #{#'rewrite-clj.node/tag}}})

(defn ns->path
  ([ns] (ns->path fs/file-separator ns))
  ([separator ns] (str/replace (namespace-munge ns) "." separator)))

(defn ns->file [ns]
  (some (fn [dir]
          (some (fn [ext]
                  (let [path (str dir fs/file-separator (ns->path ns) ext)]
                    (when (fs/exists? path)
                      path)))
                [".clj" ".cljc"]))
        (cp/classpath-directories)))

(defn normalize-filename [f]
  (if (fs/windows?)
    (-> f fs/normalize fs/unixify)
    f))

(defn ns->jar [ns]
  (let [path (ns->path "/" ns)]
    (some (fn [^java.util.jar.JarFile jar-file]
            (when (or (.getJarEntry jar-file (str path ".clj"))
                      (.getJarEntry jar-file (str path ".cljc")))
              (normalize-filename (.getName jar-file))))
          (cp/classpath-jarfiles))))

#_(ns->jar (find-ns 'weavejester.dependency))

(defn guard [x f]
  (when (f x) x))

(defn symbol->jar [sym]
  (some-> (if (qualified-symbol? sym)
            (-> sym namespace symbol)
            sym)
          ^Class resolve
          .getProtectionDomain
          .getCodeSource
          .getLocation
          ^java.net.URL (guard #(= "file" (.getProtocol ^java.net.URL %)))
          .getFile
          (guard #(str/ends-with? % ".jar"))
          normalize-filename))

#_(symbol->jar 'io.methvin.watcher.PathUtils)
#_(symbol->jar 'io.methvin.watcher.PathUtils/cast)
#_(symbol->jar 'java.net.http.HttpClient/newHttpClient)


(defn find-location [sym]
  (if (deref? sym)
    (find-location (second sym))
    (if-let [ns (and (qualified-symbol? sym) (-> sym namespace symbol find-ns))]
      (or (ns->file ns)
          (ns->jar ns))
      (symbol->jar sym))))

#_(find-location `inc)
#_(find-location '@nextjournal.clerk.webserver/!doc)
#_(find-location `dep/depend)
#_(find-location 'java.util.UUID)
#_(find-location 'java.util.UUID/randomUUID)
#_(find-location 'io.methvin.watcher.PathUtils)
#_(find-location 'io.methvin.watcher.hashing.FileHasher/DEFAULT_FILE_HASHER)
#_(find-location 'String)

(defn find-location+cache [!ns->loc sym]
  (if (deref? sym)
    (find-location+cache !ns->loc (second sym))
    (if-let [ns-sym (and (qualified-symbol? sym) (-> sym namespace symbol))]
      (or (@!ns->loc ns-sym)
          (when-let [loc (find-location sym)]
            (swap! !ns->loc assoc ns-sym loc)
            loc))
      (find-location sym))))

(def !ns->loc-cache (atom {}))

#_(reset! !ns->loc-cache {})

(defn find-location-cached [sym]
  (find-location+cache !ns->loc-cache sym))

#_(find-location-cached `inc)

(def hash-jar
  (memoize (fn [f]
             {:jar f :hash (sha1-base58 (io/input-stream f))})))

(defn ^:private merge-analysis-info [state {:as analyzed-doc :keys [->analysis-info]}]
  (reduce (fn [s {:as analyzed :keys [deps]}]
            (if (seq deps)
              (-> (reduce (partial analyze-deps analyzed) s deps)
                  (make-deps-inherit-no-cache analyzed))
              s))
          (update state :->analysis-info merge ->analysis-info)
          (vals ->analysis-info)))

#_(merge-analysis-info {:->analysis-info {:a :b}} {:->analysis-info {:c :d}})

#_(hash-jar (find-location `dep/depend))

(defn build-graph
  "Analyzes the forms in the given file and builds a dependency graph of the vars.

  Recursively decends into dependency vars as well as given they can be found in the classpath.
  "
  [doc]
  (loop [{:as state :keys [->analysis-info analyzed-file-set counter]}

         (-> (cond-> doc
               (not (:graph doc)) analyze-doc)
             (assoc :analyzed-file-set (cond-> #{} (:file doc) (conj (:file doc))))
             (assoc :counter 0))]
    (let [unhashed (unhashed-deps ->analysis-info)
          loc->syms (apply dissoc
                           (group-by find-location-cached unhashed)
                           analyzed-file-set)]
      (if (and (seq loc->syms) (< counter 10))
        (recur (-> (reduce (fn [g [source symbols]]
                             (if (or (nil? source)
                                     (str/ends-with? source ".jar"))
                               (update g :->analysis-info merge (into {} (map (juxt identity (constantly (if source (hash-jar source) {})))) symbols))
                               (-> g
                                   (update :analyzed-file-set conj source)
                                   (merge-analysis-info (analyze-file source)))))
                           state
                           loc->syms)
                   (update :counter inc)))
        (dissoc state :analyzed-file-set :counter)))))


(comment
  (def parsed (parser/parse-file {:doc? true} "src/nextjournal/clerk/webserver.clj"))
  (def analysis (time (-> parsed analyze-doc build-graph)))
  (-> analysis :->analysis-info keys set)
  (let [{:keys [->analysis-info]} analysis]
    (dissoc (group-by find-location (unhashed-deps ->analysis-info)) nil))
  (nextjournal.clerk/clear-cache!))

#_(do (time (build-graph (parser/parse-clojure-string (slurp "notebooks/how_clerk_works.clj")))) :done)
#_(do (time (build-graph (parser/parse-clojure-string (slurp "notebooks/viewer_api.clj")))) :done)

#_(analyze-file "notebooks/how_clerk_works.clj")
#_(nextjournal.clerk/show! "notebooks/how_clerk_works.clj")

#_(build-graph (parser/parse-clojure-string (slurp "notebooks/hello.clj")))
#_(keys (:->analysis-info (build-graph "notebooks/elements.clj")))
#_(dep/immediate-dependencies (:graph (build-graph "notebooks/elements.clj"))  #'nextjournal.clerk.demo/fix-case)
#_(dep/transitive-dependencies (:graph (build-graph "notebooks/elements.clj"))  #'nextjournal.clerk.demo/fix-case)

#_(keys (:->analysis-info (build-graph "src/nextjournal/clerk/analyzer.clj")))
#_(dep/topo-sort (:graph (build-graph "src/nextjournal/clerk/analyzer.clj")))
#_(dep/immediate-dependencies (:graph (build-graph "src/nextjournal/clerk/analyzer.clj"))  #'nextjournal.clerk.analyzer/long-thing)
#_(dep/transitive-dependencies (:graph (build-graph "src/nextjournal/clerk/analyzer.clj"))  #'nextjournal.clerk.analyzer/long-thing)

(defn ^:private remove-type-meta
  "Walks given `form` removing `:type` from metadata to ensure it can be printed."
  [form]
  (walk/postwalk (fn [x] (cond-> x
                           (contains? (meta x) :type)
                           (vary-meta dissoc :type)))
                 form))

(defn hash-codeblock [->hash {:as codeblock :keys [hash form id deps vars]}]
  (when (and (seq deps) (not (ifn? ->hash)))
    (throw (ex-info "`->hash` must be `ifn?`" {:->hash ->hash :codeblock codeblock})))
  (let [hashed-deps (into #{} (map ->hash) deps)]
    (sha1-base58 (binding [*print-length* nil]
                   (pr-str (set/union (conj hashed-deps (if form (remove-type-meta form) hash))
                                      vars))))))

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

(defprotocol BoundedCountCheck
  (-exceeds-bounded-count-limit? [x limit]))

(extend-protocol BoundedCountCheck
  nil (-exceeds-bounded-count-limit? [_ _] false)
  Object (-exceeds-bounded-count-limit? [_ _] false)
  clojure.lang.IPersistentCollection (-exceeds-bounded-count-limit? [x limit]
                                       (or (some #(-exceeds-bounded-count-limit? % limit) x) false))
  clojure.lang.ISeq (-exceeds-bounded-count-limit? [xs limit]
                      (or (<= limit (bounded-count limit xs))
                          (some #(-exceeds-bounded-count-limit? % limit) xs))))

(defn exceeds-bounded-count-limit? [x]
  (-exceeds-bounded-count-limit? x config/*bounded-count-limit*))

#_(time (exceeds-bounded-count-limit? viewers.table/letter->words))

(defn valuehash
  ([value] (valuehash :sha512 value))
  ([hash-type value]
   (let [digest-fn (case hash-type
                     :sha1 sha1-base58
                     :sha512 sha2-base58)]
     (-> value
         nippy/fast-freeze
         digest-fn))))

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
    (let [topo-comp (dep/topo-comparator graph)
          deref-deps-to-eval (set/difference deref-deps (-> ->hash keys set))
          doc-with-deref-dep-hashes (reduce (fn [state deref-dep]
                                              (assoc-in state [:->hash deref-dep] (->hash-str (eval deref-dep))))
                                            analyzed-doc
                                            (sort topo-comp deref-deps-to-eval))]
      #_(prn :hash-deref-deps/form form :deref-deps deref-deps-to-eval)
      (hash doc-with-deref-dep-hashes (sort topo-comp (dep/transitive-dependents-set graph deref-deps-to-eval))))
    analyzed-doc))

#_(nextjournal.clerk/show! "notebooks/hash_fn.clj")

#_(do (swap! hash-fn/!state inc)
      (nextjournal.clerk/recompute!))

#_(deref nextjournal.clerk.webserver/!doc)

#_(nextjournal.clerk/clear-cache!)

#_(def my-num 42)

#_(do (reset! scratch-recompute/!state my-num) (nextjournal.clerk/recompute!))
#_(do (reset! scratch-recompute/!state my-num) (nextjournal.clerk/show! 'scratch-recompute))

(defn find-blocks
  "Finds the first matching block in the given `analyzed-doc` using
  `sym-or-form`:
   * when given a symbol by `:var` or `:id`
   * when given a by `:form`"
  [{:as _analyzed-doc :keys [blocks ns]} sym-or-form]
  (cond (symbol? sym-or-form)
        (let [qualified-symbol (if (qualified-symbol? sym-or-form)
                                 sym-or-form
                                 (symbol (str ns) (name sym-or-form)))]
          (filter #(or (= qualified-symbol (:var %))
                       (= qualified-symbol (:id %)))
                  blocks))
        (seq? sym-or-form)
        (filter #(= sym-or-form (:form %)) blocks)))

#_(find-blocks @nextjournal.clerk.webserver/!doc 'scratch/foo)
#_(find-blocks @nextjournal.clerk.webserver/!doc 'foo)
#_(find-blocks @nextjournal.clerk.webserver/!doc '(rand-int 1000))
