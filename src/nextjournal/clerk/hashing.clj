;; # Hashing Things!!!!
(ns ^:nextjournal.clerk/no-cache nextjournal.clerk.hashing
  (:refer-clojure :exclude [hash read-string])
  (:require [babashka.fs :as fs]
            [clojure.core :as core]
            [clojure.java.io :as io]
            [clojure.set :as set]
            [clojure.string :as str]
            [clojure.tools.analyzer.jvm :as ana]
            [clojure.tools.analyzer.passes.jvm.emit-form :as ana.passes.ef]
            [edamame.core :as edamame]
            [multihash.core :as multihash]
            [multihash.digest :as digest]
            [rewrite-clj.node :as n]
            [rewrite-clj.parser :as p]
            [nextjournal.clerk.classpath :as cp]
            [nextjournal.clerk.config :as config]
            [nextjournal.markdown :as markdown]
            [nextjournal.markdown.parser :as markdown.parser]
            [nextjournal.markdown.transform :as markdown.transform]
            [taoensso.nippy :as nippy]
            [weavejester.dependency :as dep]))

(defn var-name
  "Takes a `analyzed-form` and returns the name of the var, if it exists."
  [analyzed-form]
  (when (and (seq? analyzed-form)
             (= 'def (first analyzed-form)))
    (second analyzed-form)))

(defn not-quoted? [form]
  (not (= 'quote (first form))))

(defn defined-vars [analyzed-form]
  (into #{}
        (keep var-name)
        (tree-seq (every-pred sequential? not-quoted?) seq analyzed-form)))

#_(:vars (analyze '(do (def foo :bar) (let [x (defn bar [] :baz)]) (defonce !state (atom {})))))

(defn ns? [form]
  (and (seq? form) (= 'ns (first form))))

(defn no-cache? [form]
  (let [var-or-form    (if-let [vn (var-name form)] vn form)
        no-cache-meta? (comp boolean :nextjournal.clerk/no-cache meta)]
    (or (no-cache-meta? var-or-form)
        (when-not (ns? form)
          (no-cache-meta? *ns*))
        (and (seq? form) (= `deref (first form))))))

#_(no-cache? '(rand-int 10))

(defn sha1-base58 [s]
  (-> s digest/sha1 multihash/base58))

#_(sha1-base58 "hello")

(defn var-dependencies [analyzed-form]
  (let [var-name (var-name analyzed-form)]
    (into #{}
          (filter #(and (symbol? %)
                        (if (qualified-symbol? %)
                          (not= var-name %)
                          (and (not= \. (-> % str (.charAt 0)))
                               (-> % resolve class?)))))
          (tree-seq (every-pred (some-fn sequential? map? set?) not-quoted?) seq analyzed-form))))

#_(var-dependencies '(def nextjournal.clerk.hashing/foo
                       (fn* ([] (nextjournal.clerk.hashing/foo "s"))
                            ([s] (clojure.string/includes?
                                  (rewrite-clj.parser/parse-string-all s) "hi")))))
(defn analyze+emit [form]
  (-> form
      ana/analyze
      (ana.passes.ef/emit-form #{:hygenic :qualified-symbols})))

(defn rewrite-defcached [form]
  (if (and (list? form)
           (symbol? (first form))
           (= (resolve 'nextjournal.clerk/defcached)
              (resolve (first form))))
    (conj (rest form) 'def)
    form))

#_(rewrite-defcached '(nextjournal.clerk/defcached foo :bar))

(defn def? [form]
  (and (seq? form) (= 'def (first form))))

(defn deflike? [form]
  (and (seq? form) (symbol? (first form)) (str/starts-with? (name (first form)) "def")))

#_(deflike? '(defonce foo :bar))
#_(deflike? '(rdef foo :bar))

(defn analyze [form]
  (binding [config/*in-clerk* true]
    (let [analyzed-form (analyze+emit (rewrite-defcached form))
          vars (defined-vars analyzed-form)
          deps (apply disj (var-dependencies analyzed-form) vars)]
      (cond-> {:form form
               ;; TODO: drop var downstream so hash stays stable under change
               :ns-effect? (some? (some #{'clojure.core/require 'clojure.core/in-ns} deps))
               :no-cache? (no-cache? form)}
        vars (assoc :vars vars)
        (and (= 1 (count vars)) (or (def? analyzed-form)
                                    (deflike? form))) (assoc :var (first vars))
        (seq deps) (assoc :deps deps)))))

#_(:vars (analyze '(do (def a 41) (def b (inc a)))))
#_(:vars (analyze '(defrecord Node [v l r])))
#_(analyze '(defn foo [s] (str/includes? (p/parse-string-all s) "hi")))
#_(analyze '(defn segments [s] (let [segments (str/split s)]
                                 (str/join segments))))
#_(analyze '(v/md "It's **markdown**!"))
#_(analyze '(in-ns 'user))
#_(analyze '(do (ns foo)))
#_(analyze '(def my-inc inc))
#_(analyze '(defn my-inc
              ([] (my-inc 0))
              ([x] (inc x))))
#_(analyze '(defonce !state (atom {})))
#_(analyze '(do (def foo :bar) (def foo-2 :bar)))

(defn remove-leading-semicolons [s]
  (str/replace s #"^[;]+" ""))

(defn ->visibility [form]
  (when-let [visibility (-> form meta :nextjournal.clerk/visibility)]
    (let [visibility-set (cond-> visibility (not (set? visibility)) hash-set)]
      (when-not (every? #{:hide-ns :fold-ns :hide :show :fold} visibility-set)
        (throw (ex-info "Invalid `:nextjournal.clerk/visibility`, valid values are `#{:hide-ns :fold-ns :hide :show :fold}`." {:visibility visibility :form form})))
      (when (and (or (visibility-set :hide-ns) (visibility-set :fold-ns))
                 (not (ns? form)))
        (throw (ex-info "Cannot set `:nextjournal.clerk/visibility` to `:hide-ns` or `:fold-ns` on non ns form." {:visibility visibility :form form})))
      visibility-set)))

#_(->visibility '(foo :bar))
#_(->visibility (quote ^{:nextjournal.clerk/visibility :fold} (ns foo)))
#_(->visibility (quote ^{:nextjournal.clerk/visibility #{:hide-ns :fold}} (ns foo)))
#_(->visibility (quote ^{:nextjournal.clerk/visibility :hidden} (ns foo)))
#_(->visibility (quote ^{:nextjournal.clerk/visibility "bam"} (ns foo)))
#_(->visibility (quote ^{:nextjournal.clerk/visibility #{:hide-ns}} (do :foo)))

(defn ->doc-visibility [first-form]
  (or (when (ns? first-form)
        (-> first-form
            ->visibility
            (disj :hide-ns :fold-ns)
            not-empty))
      #{:show}))

#_(->doc-visibility '^{:nextjournal.clerk/visibility :fold} (ns foo))
#_(->doc-visibility '^{:nextjournal.clerk/visibility :hide-ns} (ns foo))

(defn ->doc-settings [first-form]
  {:visibility (->doc-visibility first-form)
   :toc (or (#{true :collapsed} (-> first-form meta :nextjournal.clerk/toc)) false)})

#_(->doc-settings '(ns foo))
#_(->doc-settings '^{:nextjournal.clerk/toc true} (ns foo))
#_(->doc-settings '^{:nextjournal.clerk/toc :pin} (ns foo))
#_(->doc-settings '^{:nextjournal.clerk/toc :boom} (ns foo)) ;; TODO: error

(defn auto-resolves [ns]
  (as-> (ns-aliases ns) $
    (assoc $ :current (ns-name *ns*))
    (zipmap (keys $)
            (map ns-name (vals $)))))

#_(auto-resolves (find-ns 'rule-30))

(defn read-string [s]
  (edamame/parse-string s {:all true
                           :auto-resolve (auto-resolves (or *ns* (find-ns 'user)))
                           :readers *data-readers*
                           :read-cond :allow
                           :regex #(list `re-pattern %)
                           :features #{:clj}}))

#_(read-string "(ns rule-30 (:require [nextjournal.clerk.viewer :as v]))")

(def code-tags
  #{:deref :map :meta :list :quote :reader-macro :set :token :var :vector})

(defn ->codeblock [visibility node]
  (cond-> {:type :code :text (n/string node)}
    (and (not visibility) (-> node n/string read-string ns?))
    (assoc :ns? true)))

(defn parse-clojure-string
  ([s] (parse-clojure-string {} s))
  ([{:as _opts :keys [doc?]} s]
   (loop [{:as state :keys [nodes blocks visibility]} {:nodes (:children (p/parse-string-all s))
                                                       :blocks []}]
     (if-let [node (first nodes)]
       (recur (cond
                (code-tags (n/tag node))
                (cond-> (-> state
                            (update :nodes rest)
                            (update :blocks conj (->codeblock visibility node)))
                  (not visibility)
                  (merge (-> node n/string read-string ->doc-settings)))

                (and doc? (n/comment? node))
                (-> state
                    (assoc :nodes (drop-while (some-fn n/comment? n/linebreak?) nodes))
                    (update :blocks conj {:type :markdown
                                          :doc (-> (apply str (map (comp remove-leading-semicolons n/string)
                                                                   (take-while (some-fn n/comment? n/linebreak?) nodes)))
                                                   markdown/parse
                                                   (select-keys [:type :content]))}))
                :else
                (update state :nodes rest)))
       (merge (select-keys state [:blocks :visibility])
              (when doc?
                (-> {:content (into []
                                    (comp (filter (comp #{:markdown} :type))
                                          (mapcat (comp :content :doc)))
                                    blocks)}
                    markdown.parser/add-title+toc
                    (select-keys #{:title :toc})
                    (assoc-in [:toc :mode] (:toc state)))))))))

#_(keys (parse-clojure-string {:doc? true} (slurp "notebooks/viewer_api.clj")))

(defn code-cell? [{:as node :keys [type]}]
  (and (= :code type) (contains? node :info)))

(defn parse-markdown-cell [state markdown-code-cell]
  (reduce (fn [{:as state :keys [visibility]} node]
            (-> state
                (update :blocks conj (->codeblock visibility node))
                (cond-> (not visibility) (merge (-> node n/string read-string ->doc-settings)))))
          state
          (-> markdown-code-cell markdown.transform/->text str/trim p/parse-string-all :children
              (->> (filter (comp code-tags n/tag))))))

(defn parse-markdown-string [{:keys [doc?]} s]
  (let [{:keys [content toc title]} (markdown/parse s)]
    (loop [{:as state :keys [nodes] ::keys [md-slice]} {:blocks [] ::md-slice [] :nodes content}]
      (if-some [node (first nodes)]
        (recur
         (if (code-cell? node)
           (cond-> state
             (seq md-slice)
             (-> #_state
                 (update :blocks conj {:type :markdown :doc {:type :doc :content md-slice}})
                 (assoc ::md-slice []))

             :always
             (-> #_state
                 (parse-markdown-cell node)
                 (update :nodes rest)))

           (-> state (update :nodes rest) (cond-> doc? (update ::md-slice conj node)))))

        (-> state
            (update :blocks #(cond-> % (seq md-slice) (conj {:type :markdown :doc {:type :doc :content md-slice}})))
            (select-keys [:blocks :visibility])
            (merge (when doc? (cond-> {:title title} (:toc state) (assoc :toc toc)))))))))

(defn parse-file
  ([file] (parse-file {} file))
  ([opts file] (-> (if (str/ends-with? file ".md")
                     (parse-markdown-string opts (slurp file))
                     (parse-clojure-string opts (slurp file)))
                   (assoc :file file))))

#_(parse-file {:doc? true} "notebooks/visibility.clj")
#_(parse-file "notebooks/visibility.clj")
#_(parse-file "notebooks/elements.clj")
#_(parse-file "notebooks/markdown.md")
#_(parse-file {:doc? true} "notebooks/rule_30.clj")
#_(parse-file "notebooks/src/demo/lib.cljc")

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

(defn analyze-doc
  ([doc]
   (analyze-doc {:doc? true :graph (dep/graph)} doc))
  ([{:as state :keys [doc?]} doc]
   (reduce (fn [state i]
             (let [{:keys [type text]} (get-in state [:blocks i])]
               (if (not= type :code)
                 state
                 (let [form (read-string text)
                       {:as analyzed :keys [vars deps ns-effect?]} (cond-> (analyze form)
                                                                     (:file doc) (assoc :file (:file doc)))
                       state (cond-> (reduce (fn [state ana-key]
                                               (assoc-in state [:->analysis-info ana-key] analyzed))
                                             (dissoc state :doc?)
                                             (->ana-keys analyzed))
                               doc? (update-in [:blocks i] merge (dissoc analyzed :deps :no-cache? :ns-effect?)))]
                   (when ns-effect?
                     (eval form))
                   (if (seq deps)
                     (-> (reduce (partial analyze-deps analyzed) state deps)
                         (make-deps-inherit-no-cache analyzed))
                     state)))))
           (cond-> state
             doc? (merge doc))
           (-> doc :blocks count range))))

#_(let [doc (parse-clojure-string {:doc? true} "(ns foo) (def a 41) (def b (inc a)) (do (def c 4) (def d (inc a)))")]
    (analyze-doc doc))

(defn analyze-file
  ([file] (analyze-file {:graph (dep/graph)} file))
  ([state file] (analyze-doc state (parse-file {} file))))

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

;; TODO: handle cljc files
(defn ns->file [ns]
  ;; TODO: fix case upstream when ns can be nil because var can contain java classes like java.lang.String
  (when ns
    (some (fn [dir]
            (when-let [path (str dir fs/file-separator (ns->path ns) ".clj")]
              (when (fs/exists? path)
                path)))
          (cp/classpath-directories))))

#_(ns->file (find-ns 'nextjournal.clerk.hashing))

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
  (if-let [ns (and (qualified-symbol? sym) (-> sym namespace symbol find-ns))]
    (or (ns->file ns)
        (ns->jar ns))
    (symbol->jar sym)))


#_(find-location `inc)
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


#_(build-graph (parse-clojure-string (slurp "notebooks/hello.clj")))
#_(build-graph (parse-clojure-string (slurp "notebooks/test123.clj")))
#_(keys (:->analysis-info (build-graph "notebooks/elements.clj")))
#_(dep/immediate-dependencies (:graph (build-graph "notebooks/elements.clj"))  #'nextjournal.clerk.demo/fix-case)
#_(dep/transitive-dependencies (:graph (build-graph "notebooks/elements.clj"))  #'nextjournal.clerk.demo/fix-case)

#_(keys (:->analysis-info (build-graph "src/nextjournal/clerk/hashing.clj")))
#_(dep/topo-sort (:graph (build-graph "src/nextjournal/clerk/hashing.clj")))
#_(dep/immediate-dependencies (:graph (build-graph "src/nextjournal/clerk/hashing.clj"))  #'nextjournal.clerk.hashing/long-thing)
#_(dep/transitive-dependencies (:graph (build-graph "src/nextjournal/clerk/hashing.clj"))  #'nextjournal.clerk.hashing/long-thing)

(defn hash-codeblock [->hash {:keys [hash form deps]}]
  (let [hashed-deps (into #{} (map ->hash) deps)]
    (sha1-base58 (pr-str (conj hashed-deps (if form form hash))))))

(defn hash [{:keys [->analysis-info graph]}]
  (reduce (fn [->hash k]
            (if-let [codeblock (get ->analysis-info k)]
              (assoc ->hash k (hash-codeblock ->hash codeblock))
              ->hash))
          {}
          (dep/topo-sort graph)))

#_(hash (build-graph (parse-clojure-string (slurp "notebooks/hello.clj"))))

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

(defn valuehash [value]
  (-> value
      nippy/fast-freeze
      digest/sha2-512
      multihash/base58))

#_(valuehash (range 100))
#_(valuehash (zipmap (range 100) (range 100)))

(defn ->hash-str
  "Attempts to compute a hash of `value` falling back to a random string."
  [value]
  (if-let [valuehash (try
                       (when-not (exceeds-bounded-count-limit? value)
                         (valuehash value))
                       (catch Exception _))]
    valuehash
    (str (gensym))))

#_(->hash-str (range 104))
#_(->hash-str (range))

(comment
  (require 'clojure.data)
  (let [file "notebooks/cache.clj"
        g1 (build-graph file)
        g2 (build-graph file)]
    [:= (= g1 g2)
     :diff (clojure.data/diff g1 g2)]))
