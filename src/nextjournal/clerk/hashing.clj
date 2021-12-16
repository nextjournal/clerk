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
            [nextjournal.markdown.transform :as markdown.transform]
            [weavejester.dependency :as dep]))

(defn var-name
  "Takes a `form` and returns the name of the var, if it exists."
  [form]
  (when (and (seq? form)
             (contains? '#{def defn} (first form)))
    (second form)))

(defn no-cache? [form]
  (let [var-or-form    (if-let [vn (var-name form)] vn form)
        no-cache-meta? (comp boolean :nextjournal.clerk/no-cache meta)]
    (or (no-cache-meta? var-or-form)
        (no-cache-meta? *ns*))))

#_(no-cache? '(rand-int 10))

(defn sha1-base58 [s]
  (-> s digest/sha1 multihash/base58))

#_(sha1-base58 "hello")

(defn var-dependencies [form]
  (let [var-name (var-name form)]
    (into #{}
          (filter #(and (symbol? %)
                        (if (qualified-symbol? %)
                          (not= var-name %)
                          (-> % resolve class?))))
          (tree-seq (every-pred sequential? #(not (= 'quote (first %)))) seq form))))

#_(var-dependencies '(def nextjournal.clerk.hashing/foo
                       (fn* ([] (nextjournal.clerk.hashing/foo "s"))
                            ([s] (clojure.string/includes?
                                  (rewrite-clj.parser/parse-string-all s) "hi")))))

(defn analyze [form]
  (binding [config/*in-clerk* true]
    (let [analyzed-form (-> form
                            ana/analyze
                            (ana.passes.ef/emit-form #{:hygenic :qualified-symbols}))
          var (var-name analyzed-form)
          deps (cond-> (var-dependencies analyzed-form) var (disj var))]
      (cond-> {:form (cond->> form var (drop 2))
               :ns-effect? (some? (some #{'clojure.core/require 'clojure.core/in-ns} deps))}
        var (assoc :var var)
        (seq deps) (assoc :deps deps)))))

#_(analyze '(let [+ 2] +))
#_(analyze '(defn foo [s] (str/includes? (p/parse-string-all s) "hi")))
#_(analyze '(defn segments [s] (let [segments (str/split s)]
                                 (str/join segments))))
#_(analyze '(v/md "It's **markdown**!"))
#_(analyze '(in-ns 'user))
#_(analyze '(do (ns foo)))
#_(analyze '(def my-inc inc))

(defn remove-leading-semicolons [s]
  (str/replace s #"^[;]+" ""))

(defn ns? [form]
  (and (list? form) (= 'ns (first form))))

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
                           :features #{:clj}}))

#_(read-string "(ns rule-30 (:require [nextjournal.clerk.viewer :as v]))")

(def code-tags
  #{:deref :map :meta :list :quote :reader-macro :set :token :var :vector})

(defn parse-clojure-file
  ([file] (parse-clojure-file {} file))
  ([{:as _opts :keys [markdown?]} file]
   (loop [{:as state :keys [nodes visibility]} {:nodes (:children (p/parse-file-all file))
                                                :doc []}]
     (if-let [node (first nodes)]
       (recur (cond
                (code-tags (n/tag node))
                (cond-> (-> state
                            (update :nodes rest)
                            (update :doc (fnil conj []) (cond-> {:type :code :text (n/string node)}
                                                          (and (not visibility) (-> node n/string read-string ns?))
                                                          (assoc :ns? true))))
                  (not visibility)
                  (assoc :visibility (-> node n/string read-string ->doc-visibility)))

                (and markdown? (n/comment? node))
                (-> state
                    (assoc :nodes (drop-while n/comment? nodes))
                    (update :doc conj {:type :markdown :doc (markdown/parse (apply str (map (comp remove-leading-semicolons n/string)
                                                                                            (take-while n/comment? nodes))))}))
                :else
                (update state :nodes rest)))
       (select-keys state [:doc :visibility])))))

(defn code-cell? [{:as node :keys [type]}]
  (and (= :code type) (contains? node :info)))

(defn parse-markdown-cell [state markdown-code-cell]
  (reduce (fn [{:as state :keys [visibility]} node]
            (-> state
                (update :doc conj (cond-> {:type :code :text (n/string node)}
                                    (and (not visibility) (-> node n/string read-string ns?)) (assoc :ns? true)))
                (cond-> (not visibility) (assoc :visibility (-> node n/string read-string ->doc-visibility)))))
          state
          (-> markdown-code-cell markdown.transform/->text str/trim p/parse-string-all :children
              (->> (filter (comp code-tags n/tag))))))

(defn parse-markdown-file [{:keys [markdown?]} file]
  (loop [{:as state :keys [nodes] ::keys [md-slice]} {:doc [] ::md-slice [] :nodes (:content (markdown/parse (slurp file)))}]
    (if-some [node (first nodes)]
      (recur
       (if (code-cell? node)
         (cond-> state
           (seq md-slice)
           (-> #_state
               (update :doc conj {:type :markdown :doc {:type :doc :content md-slice}})
               (assoc ::md-slice []))

           :always
           (-> #_state
               (parse-markdown-cell node)
               (update :nodes rest)))

         (-> state (update :nodes rest) (cond-> markdown? (update ::md-slice conj node)))))

      (-> state
          (update :doc #(cond-> % (seq md-slice) (conj {:type :markdown :doc {:type :doc :content md-slice}})))
          (select-keys [:doc :visibility])))))

(defn parse-file
  ([file] (parse-file {} file))
  ([opts file] (if (str/ends-with? file ".md")
                 (parse-markdown-file opts file)
                 (parse-clojure-file opts file))))

#_(parse-file {:markdown? true} "notebooks/visibility.clj")
#_(parse-file "notebooks/elements.clj")
#_(parse-file "notebooks/markdown.md")
#_(parse-file {:markdown? true} "notebooks/rule_30.clj")
#_(parse-file "notebooks/src/demo/lib.cljc")

(defn- circular-dependency-error? [e]
  (-> e ex-data :reason #{::dep/circular-dependency}))

(defn- analyze-circular-dependency [state var form dep {:keys [node dependency]}]
  (let [rec-form (concat '(do) [form (get-in state [:->analysis-info dependency :form])])
        rec-var (symbol (str var "+" dep))]
    (-> state
        (update :graph #(-> %
                            (dep/remove-edge dependency node)
                            (dep/depend var rec-var)
                            (dep/depend dep rec-var)))
        (assoc-in [:->analysis-info rec-var :form] rec-form))))

(defn- analyze-deps [var form {:as state :keys [graph]} dep]
  (try (assoc state :graph (dep/depend graph (if var var form) dep))
       (catch Exception e
         (when-not (circular-dependency-error? e)
           (throw e))
         (analyze-circular-dependency state var form dep (ex-data e)))))

(defn- analyze-codeblock [file state {:keys [type text]}]
  (let [{:keys [var deps form ns-effect?]} (-> text read-string analyze)
        state-with-var-hash (assoc-in state
                                      [:->analysis-info (if var var form)]
                                      {:file file
                                       :form form
                                       :deps deps})]
    (when ns-effect?
      (eval form))
    (if (seq deps)
      (reduce (partial analyze-deps var form)
              state-with-var-hash
              deps)
      state-with-var-hash)))

(defn analyze-file
  ([file]
   (analyze-file {} {:graph (dep/graph)} file))
  ([state file]
   (analyze-file {} state file))
  ([{:as opts :keys [markdown?]} state file]
   (let [doc                 (parse-file opts file)
         state-with-document (cond-> state markdown? (assoc :doc doc))
         code-cells (into [] (filter (comp #{:code} :type)) (:doc doc))]
     (reduce (partial analyze-codeblock file) state-with-document code-cells))))

#_(:graph (analyze-file {:markdown? true} {:graph (dep/graph)} "notebooks/elements.clj"))
#_(analyze-file {:markdown? true} {:graph (dep/graph)} "notebooks/rule_30.clj")
#_(analyze-file {:graph (dep/graph)} "notebooks/recursive.clj")
#_(analyze-file {:graph (dep/graph)} "notebooks/hello.clj")

(defn unhashed-deps [->analysis-info]
  (set/difference (into #{}
                        (mapcat :deps)
                        (vals ->analysis-info))
                  (-> ->analysis-info keys set)))

#_(unhashed-deps {#'elements/fix-case {:deps #{#'rewrite-clj.node/tag}}})

(defn- ns->path [ns]
  (str/replace (str ns) "." fs/file-separator))

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

(defn symbol->jar [sym]
  (some-> (if (qualified-symbol? sym)
            (-> sym namespace symbol)
            sym)
          resolve
          .getProtectionDomain
          .getCodeSource
          .getLocation
          .getFile))


#_(symbol->jar 'io.methvin.watcher.PathUtils)
#_(symbol->jar 'io.methvin.watcher.PathUtils/cast)


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
  [file]
  (let [{:as graph :keys [->analysis-info]} (analyze-file file)]
    (reduce (fn [g [source symbols]]
              (if (or (nil? source)
                      (str/ends-with? source ".jar"))
                (update g :->analysis-info merge (into {} (map (juxt identity (constantly (if source (hash-jar source) {})))) symbols))
                (analyze-file g source)))
            graph
            (group-by find-location (unhashed-deps ->analysis-info)))))


#_(build-graph "notebooks/hello.clj")
#_(keys (:->analysis-info (build-graph "notebooks/elements.clj")))
#_(dep/immediate-dependencies (:graph (build-graph "notebooks/elements.clj"))  #'nextjournal.clerk.demo/fix-case)
#_(dep/transitive-dependencies (:graph (build-graph "notebooks/elements.clj"))  #'nextjournal.clerk.demo/fix-case)

#_(keys (:->analysis-info (build-graph "src/nextjournal/clerk/hashing.clj")))
#_(dep/topo-sort (:graph (build-graph "src/nextjournal/clerk/hashing.clj")))
#_(dep/immediate-dependencies (:graph (build-graph "src/nextjournal/clerk/hashing.clj"))  #'nextjournal.clerk.hashing/long-thing)
#_(dep/transitive-dependencies (:graph (build-graph "src/nextjournal/clerk/hashing.clj"))  #'nextjournal.clerk.hashing/long-thing)


(defn hash
  ([file]
   (let [{vars :->analysis-info :keys [graph]} (build-graph file)]
     (reduce (fn [vars->hash var]
               (if-let [info (get vars var)]
                 (assoc vars->hash var (hash vars->hash (assoc info :var var)))
                 vars->hash))
             {}
             (dep/topo-sort graph))))
  ([->analysis-info {:keys [hash form deps]}]
   (let [hashed-deps (into #{} (map ->analysis-info) deps)]
     (sha1-base58 (pr-str (conj hashed-deps (if form form hash)))))))

#_(hash "notebooks/hello.clj")
#_(hash "notebooks/elements.clj")
#_(clojure.data/diff (hash "notebooks/how_clerk_works.clj")
                     (hash "notebooks/how_clerk_works.clj"))

(comment
  (require 'clojure.data)
  (let [file "notebooks/cache.clj"
        g1 (build-graph file)
        g2 (build-graph file)]
    [:= (= g1 g2)
     :diff (clojure.data/diff g1 g2)]))
