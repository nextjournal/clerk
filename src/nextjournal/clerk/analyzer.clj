(ns nextjournal.clerk.analyzer
  {:nextjournal.clerk/no-cache true}
  (:refer-clojure :exclude [hash])
  (:require [babashka.fs :as fs]
            [clojure.core :as core]
            [clojure.java.io :as io]
            [clojure.set :as set]
            [clojure.string :as str]
            [multiformats.hash :as hash]
            [nextjournal.clerk.analyzer.impl :as ana :refer [analyze*]]
            [nextjournal.clerk.classpath :as cp]
            [nextjournal.clerk.config :as config]
            [nextjournal.clerk.parser :as parser]
            [nextjournal.clerk.utils :as utils]
            [nextjournal.clerk.walk :as walk]
            [weavejester.dependency :as dep]))

(when-not utils/bb?
  (require '[taoensso.nippy :as nippy]))

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
  (->> s hash/sha1 hash/encode (utils/->base58)))

(defn sha2-base58 [s]
  (->> s hash/sha2-512 hash/encode (utils/->base58)))

#_(sha1-base58 "hello")

#_(map type (:deps (analyze '(+ 1 2))))

(defn rewrite-defcached [form]
  (if (and (list? form)
           (symbol? (first form))
           (= (resolve 'nextjournal.clerk/defcached)
              (resolve (first form))))
    (conj (rest form) 'def)
    form))

#_(rewrite-defcached '(nextjournal.clerk/defcached foo :bar))

(defn form->ex-data
  "Returns ex-data map with the form and its location info from metadata."
  [form]
  (merge (select-keys (meta form) [:line :col :clojure.core/eval-file])
         {:form form}))

(defn- analyze-form*
  ([form] (analyze-form* {} form))
  ([bindings form]
   (binding [config/*in-clerk* true]
     (try
       (analyze* (assoc (ana/to-env bindings)
                        :ns (ns-name *ns*)) form)
       (catch java.lang.AssertionError e
         (throw (ex-info "Failed to analyze form"
                         (form->ex-data form)
                         e)))))))

(defn analyze-form [form]
  (with-bindings (utils/if-bb
                   {}
                   {clojure.lang.Compiler/LOADER (clojure.lang.RT/makeClassLoader)})
    (binding [ana/*deps* (or ana/*deps* (atom #{}))]
      (analyze-form* (rewrite-defcached form)))))

(defn ^:private var->protocol [v]
  (or (let [p (:protocol (meta v))]
        (when (var? p) p))
      v))

(defn get-vars+forward-declarations [nodes]
  (reduce (fn reduce-vars [{:as acc seen :vars} {:as _node v :var m :meta}]
            (let [sym (symbol v)
                  acc' (update acc :vars conj sym)
                  declared? (-> m :val :declared)]
              (cond
                (and (not (seen sym)) declared?)
                (update acc' :declared conj sym)
                (and (seen sym) (not declared?))
                (update acc' :declared disj sym)
                :else acc')))
          {:vars #{} :declared #{}}
          (filter (every-pred (comp #{:def} :op) :var)
                  nodes)))

(defn analyze [form]
  (let [!deps      (atom #{})
        analyzed (binding [ana/*deps* !deps]
                   (analyze-form form))
        _ (ana/prewalk (ana/only-nodes
                        #{:var :binding :symbol}
                        (fn [var-node]
                          (case (:op var-node)
                            :var (let [var (:var var-node)]
                                   (swap! !deps conj var))
                            :binding (when-let [t (:tag (meta (:form var-node)))]
                                       (when-let [clazz (try (resolve t)
                                                             (catch Exception _ nil))]
                                         (when (class? clazz)
                                           (swap! !deps conj (.getName ^Class clazz)))))
                            :symbol (when-not (:local? var-node)
                                      (let [form (:form var-node)]
                                        (if (qualified-symbol? form)
                                          (let [clazz-sym (symbol (namespace form))]
                                            (when-let [clazz (try (resolve clazz-sym)
                                                                  (catch Exception _ nil))]
                                              (when (class? clazz)
                                                (swap! !deps conj (.getName ^Class clazz)))))
                                          (when-let [clazz (try (resolve form)
                                                                (catch Exception _ nil))]
                                            (when (class? clazz)
                                              (swap! !deps conj (.getName ^Class clazz))))))))
                          var-node)) analyzed)
        nodes (ana/nodes analyzed)
        {:keys [vars declared]} (get-vars+forward-declarations nodes)
        vars- (set/difference vars declared)
        var (when (and (= 1 (count vars))
                       (parser/deflike? form))
              (first vars))
        def-node (when var
                   (first (filter (comp #{:def} :op) nodes)))
        deref-deps (into #{}
                         (comp (filter (comp #{#'deref} :var :fn))
                               (keep #(-> % :args first))
                               (filter :var)
                               (keep (fn [{:keys [op var]}]
                                       (when-not (= :the-var op)
                                         (list `deref (symbol var))))))
                         nodes)
        ;; TODO: check case '(def a (inc a)) deps are empty for this which is wrong
        deps (set/union (set/difference (into #{} (map (comp symbol var->protocol)) @!deps) vars)
                        deref-deps
                        (when (var? form) #{(symbol form)}))
        hash-fn (-> form meta :nextjournal.clerk/hash-fn)
        macro? (-> analyzed :env :defmacro)]
    (cond-> {#_#_:analyzed analyzed
             :form form
             :ns-effect? (some? (some #{'clojure.core/require 'clojure.core/in-ns} deps))
             :freezable? (and (not (some #{'clojure.core/intern} deps))
                              (<= (count vars) 1)
                              (if (seq vars) (= var (first vars)) true))
             :no-cache? (no-cache? form (-> def-node :form second) *ns*)
             :macro macro?}
      hash-fn (assoc :hash-fn hash-fn)
      (seq deps) (assoc :deps deps)
      (seq deref-deps) (assoc :deref-deps deref-deps)
      (seq vars) (assoc :vars vars)
      (seq vars-) (assoc :vars- vars-)
      (seq declared) (assoc :declared declared)
      var (assoc :var var))))

#_(:vars     (analyze '(do (declare x y) (def x 0) (def z) (def w 0)))) ;=> x y z w
#_(:vars-    (analyze '(do (def x 0) (declare x y) (def z) (def w 0)))) ;=> x z w
#_(:vars-    (analyze '(do (declare x y) (def x 0) (def z) (def w 0)))) ;=> x z w
#_(:declared (analyze '(do (declare x y) (def x 0) (def z) (def w 0)))) ;=> y

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
    ;;(when (not= dep dependency) (println :dep-mismatch dep dependency))
    ;;(when (not= var node) (println :node-mismatch var node))
    (-> state
        (update :graph #(-> %
                            (dep/remove-edge dependency node)
                            (dep/depend var rec-var)
                            (dep/depend dep rec-var)))
        (assoc-in [:->analysis-info rec-var :form] rec-form))))

#_(->> (nextjournal.clerk.eval/eval-string "(rand-int 100) (rand-int 100) (rand-int 100)") :blocks (mapv #(-> % :result :nextjournal/value)))

(defn dep->block-id
  "Inverse key-lookup from deps to block ids"
  [{:keys [var->block-id]} dep]
  (or (get var->block-id dep) dep))

(defn deps->block-ids [state {:as _info :keys [deps]}]
  (into #{} (map (partial dep->block-id state)) deps))

(defn- analyze-deps [{:as _info :keys [id form vars]} state dep]
  (try (update state :graph dep/depend id (dep->block-id state dep))
       (catch Exception e
         (if (circular-dependency-error? e)
           (analyze-circular-dependency state vars form dep (ex-data e))
           (throw e)))))

(defn make-deps-inherit-no-cache [{:as doc :keys [ns ->analysis-info blocks]}]
  (let [nsname (when ns (str (ns-name ns)))]
    (reduce (fn no-cache-inheritance-rf [state {:as analyzed :keys [id no-cache?]}]
              (if no-cache?
                state
                (assoc-in state [:->analysis-info id :no-cache?]
                          (boolean (some (fn [k]
                                           (and ns (symbol? k) (= nsname (namespace k))
                                                (get-in state [:->analysis-info k :no-cache?])))
                                         (deps->block-ids state analyzed))))))
            doc
            (keep (comp #(get ->analysis-info %) :id)
                  (filter (comp #{:code} :type)
                          blocks)))))

(defn ^:private internal-proxy-name?
  "Returns true if `sym` represents a var name interned by `clojure.core/proxy`."
  [sym]
  (str/includes? (name sym) ".proxy$"))

(defn throw-if-dep-is-missing [{:as doc :keys [blocks ns error-on-missing-vars ->analysis-info file]}]
  (when (= :on error-on-missing-vars)
    (let [block-ids (into #{} (keep :id) blocks)
          ;; only take current blocks into account
          current-analyis (into {} (filter (comp block-ids :id val) ->analysis-info))
          defined (set/union (-> current-analyis keys set)
                             (into #{} (mapcat (comp :nextjournal/interned :result)) blocks))]
      (doseq [{:keys [form deps]} (vals current-analyis)]
        (when (seq deps)
          (when-some [missing-dep (first (set/difference (into #{}
                                                               (comp (map (partial dep->block-id doc))
                                                                     (filter #(and (symbol? %) (= (-> ns ns-name name) (namespace %))))
                                                                     (remove internal-proxy-name?))
                                                               deps)
                                                         defined))]
            (throw (ex-info (str "The var `#'" missing-dep "` is being referenced, but Clerk can't find it in the namespace's source code. Did you remove it? This validation can fail when the namespace is mutated programmatically (e.g. using `clojure.core/intern` or side-effecting macros). You can turn off this check by adding `{:nextjournal.clerk/error-on-missing-vars :off}` to the namespace metadata.")
                            {:var-name missing-dep :form form :file file #_#_:defined defined}))))))))

(defn ns-resolver [notebook-ns]
  (if notebook-ns
    (into {} (map (juxt key (comp ns-name val))) (ns-aliases notebook-ns))
    identity))
#_ (ns-resolver *ns*)

(defn analyze-doc-deps [{:as doc :keys [->analysis-info]}]
  (reduce (fn [state {:as info :keys [deps]}]
            (reduce (partial analyze-deps info) state deps))
          doc
          (vals ->analysis-info)))

(defn track-var->block+redefs [{:as state seen :var->block-id} {:keys [id vars-]}]
  (-> state
      (update :var->block-id (partial reduce (fn [m v] (assoc m v id))) vars-)
      (update :redefs into (set/intersection vars- (set (keys seen))))))

(defn info-store-keys [{:keys [id vars-]}] (cons id vars-))

(defn assoc-new [m k v] (cond-> m (not (contains? m k)) (assoc k v)))

(defn store-info [state info]
  (reduce #(update %1 :->analysis-info assoc-new %2 info)
          state
          (info-store-keys info)))

(defn extract-file
  "Extracts the string file path from the given `resource` to for usage
  on the `:clojure.core/eval-file` form meta key."
  [^java.net.URL resource]
  (case (.getProtocol resource)
    "file" (str (.getFile resource))
    "jar" (str (.getJarEntry ^java.net.JarURLConnection (.openConnection resource)))))

#_(extract-file (io/resource "clojure/core.clj"))
#_(extract-file (io/resource "nextjournal/clerk.clj"))

(defn analyze-doc
  "Goes through `:blocks` of `doc`, reads and analyzes block forms, populates `:->analysis-info`"
  ([doc]
   (analyze-doc {:doc? true} doc))
  ([{:as state :keys [doc?]} doc]
   (binding [*ns* *ns*]
     (let [add-block-id (partial parser/add-block-id (atom {}))]
       (cond-> (reduce (fn [{:as state notebook-ns :ns} {:as block :keys [type text loc]}]
                         (if (not= type :code)
                           (update state :blocks conj (add-block-id block))
                           (let [{:as form-analysis :keys [ns-effect? form]} (cond-> (analyze (:form block))
                                                                               (:file doc) (assoc :file (:file doc)))
                                 block+analysis (add-block-id (merge block form-analysis))]
                             (when ns-effect? ;; needs to run before setting doc `:ns` via `*ns*`
                               (eval form))
                             (-> state
                                 (store-info block+analysis)
                                 (track-var->block+redefs block+analysis)
                                 (update :blocks conj (cond-> (dissoc block+analysis :deps :no-cache? :ns-effect?)
                                                        (parser/ns? form) (assoc :ns? true)
                                                        doc? (assoc :text-without-meta (parser/text-with-clerk-metadata-removed text (ns-resolver notebook-ns)))))))))

                       (-> state
                           (cond-> doc? (merge doc))
                           (assoc :var->block-id {}
                                  :redefs #{}
                                  :blocks []))
                       (:blocks doc))

         true (dissoc :doc?)
         doc? parser/filter-code-blocks-without-form)))))

#_(let [parsed (parser/parse-clojure-string "clojure.core/dec")]
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
  (let [ns-path (ns->path ns)]
    (some (fn [dir]
            (some (fn [ext]
                    (let [path (str dir fs/file-separator ns-path ext)]
                      (when (fs/exists? path)
                        path)))
                  [".clj" ".cljc"]))
          (cp/classpath-directories))))

(defn var->file [var]
  (when-let [file-from-var (-> var meta :file)]
    (some (fn [classpath-dir]
            (let [path (str classpath-dir fs/file-separator file-from-var)]
              (when (fs/exists? path)
                path)))
          (cp/classpath-directories))))

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

(utils/when-not-bb
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
           normalize-filename)))

#_(symbol->jar 'io.methvin.watcher.PathUtils)
#_(symbol->jar 'io.methvin.watcher.PathUtils/cast)
#_(symbol->jar 'java.net.http.HttpClient/newHttpClient)

(defn var->location [var]
  (when-let [file (:file (meta var))]
    (some-> (if (try (fs/absolute? file)
                     ;; fs/absolute? crashes in bb on Windows due to the :file
                     ;; metadata containing "<expr>"
                     (catch Exception _ false))
              (when (fs/exists? file)
                (fs/relativize (fs/cwd) (fs/file file)))
              (when-let [resource (io/resource file)]
                (let [protocol (.getProtocol resource)]
                  (or (and (= "jar" protocol)
                           (second (re-find #"^file:(.*)!" (.getFile resource))))
                      (and (= "file" protocol)
                           (.getFile resource))))))
            str)))

(defn find-location [sym]
  (if (deref? sym)
    (find-location (second sym))
    (or (some-> sym resolve var->location)
        (if-let [ns (and (qualified-symbol? sym) (-> sym namespace symbol find-ns))]
          (or (ns->file ns)
              (ns->jar ns))
          (utils/when-not-bb (symbol->jar sym))))))

#_(find-location `inc)
#_(find-location `*print-dup*)
#_(find-location '@nextjournal.clerk.webserver/!doc)
#_(find-location `dep/depend)
#_(find-location 'java.util.UUID)
#_(find-location 'java.util.UUID/randomUUID)
#_(find-location 'io.methvin.watcher.PathUtils)
#_(find-location 'io.methvin.watcher.hashing.FileHasher/DEFAULT_FILE_HASHER)
#_(find-location 'String)

(def hash-jar
  (memoize (fn [f]
             {:jar f :hash (sha1-base58 (io/input-stream f))})))

(defn ^:private merge-analysis-info [state {:as _analyzed-doc :keys [->analysis-info var->block-id]}]
  (-> state
      (update :->analysis-info merge ->analysis-info)
      (update :var->block-id merge var->block-id)))

#_(merge-analysis-info {:->analysis-info {:a :b}} {:->analysis-info {:c :d}})

#_(hash-jar (find-location `dep/depend))

(defn set-no-cache-on-redefs [{:as doc :keys [redefs blocks ->analysis-info]}]
  (reduce (fn [state {:as _info :keys [id vars- no-cache?]}]
            (cond-> state
              ;; redefinitions are never cached
              (and (not no-cache?) (seq (set/intersection vars- redefs)))
              (assoc-in [:->analysis-info id :no-cache?] true)))
          doc
          (keep (comp #(get ->analysis-info %) :id)
                (filter (comp #{:code} :type)
                        blocks))))

(defn transitive-deps
  ([id analysis-info]
   (loop [seen #{}
          deps #{id}
          res #{}]
     (if (seq deps)
       (let [dep (first deps)]
         (if (contains? seen dep)
           (recur seen (rest deps) res)
           (let [{new-deps :deps} (get analysis-info dep)
                 seen (conj seen dep)
                 deps (concat (rest deps) new-deps)
                 res (into res deps)]
             (recur seen deps res))))
       res))))

#_(transitive-deps id analysis-info)

#_(transitive-deps :main {:main {:deps [:main :other]}
                          :other {:deps [:another]}
                          :another {:deps [:another-one :another :main]}})

(defn run-macros [init-state]
  (let [{:keys [blocks ->analysis-info]} init-state
        macro-block-ids (keep #(when (:macro %)
                                 (:id %)) blocks)
        deps (mapcat #(transitive-deps % ->analysis-info) macro-block-ids)
        all-block-ids (into (set macro-block-ids) deps)
        all-blocks (filter #(contains? all-block-ids (:id %)) blocks)]
    (doseq [block all-blocks]
      (try
        (load-string (:text block))
        (catch Throwable e
          (binding [*out* *err*]
            (println "Error when evaluating macro deps:" (:text block))
            (println "Namespace:" *ns*)
            (println "Exception:" e)))))
    (pos? (count all-blocks))))

(defn build-graph
  "Analyzes the forms in the given file and builds a dependency graph of the vars.

  Recursively descends into dependency vars as well if they can be found in the classpath.
  "
  [doc]
  (let [init-state-fn #(-> doc
                           analyze-doc
                           (assoc :analyzed-file-set (cond-> #{} (:file doc) (conj (:file doc)))
                                  :counter 0
                                  :graph (dep/graph)))
        init-state (init-state-fn)
        ran-macros? false #_(run-macros init-state)
        init-state (if ran-macros?
                     (init-state-fn)
                     init-state)]
    (loop [{:as state :keys [->analysis-info analyzed-file-set counter]} init-state]
      (let [unhashed (unhashed-deps ->analysis-info)
            loc->syms (apply dissoc
                             (group-by find-location unhashed)
                             analyzed-file-set)]
        (if (and (seq loc->syms) (< counter 10))
          (recur (-> (reduce (fn [g [source symbols]]
                               (let [jar? (or (nil? source)
                                              (str/ends-with? source ".jar"))
                                     gitlib-hash (and (not jar?)
                                                      (second (re-find #".gitlibs/libs/.*/(\b[0-9a-f]{5,40}\b)/" (fs/unixify source))))]
                                 (if (or jar? gitlib-hash)
                                   (update g :->analysis-info merge (into {} (map (juxt identity
                                                                                        (constantly (if source
                                                                                                      (or (when gitlib-hash {:hash gitlib-hash})
                                                                                                          (hash-jar source))
                                                                                                      {})))) symbols))
                                   (-> g
                                       (update :analyzed-file-set conj source)
                                       (merge-analysis-info (analyze-file source))))))
                             state
                             loc->syms)
                     (update :counter inc)))
          (-> state
              analyze-doc-deps
              set-no-cache-on-redefs
              make-deps-inherit-no-cache
              (dissoc :analyzed-file-set :counter)))))))

(comment
  (reset! !file->analysis-cache {})

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


(defn ^:private canonicalize-form
  "Undoes the non-deterministic transformations done by the splicing reader macro."
  [form]
  (walk/postwalk (fn [f]
                   (if-let [orig-name (and (simple-symbol? f)
                                           (second (re-matches #"(.*)__\d+__auto__" (name f))))]
                     (symbol (str orig-name "#"))
                     f))
                 form))

(comment
  (canonicalize-form `foo-bar###)
  (canonicalize-form `(let [a# 1]
                        (inc a#))))

(defn hash-codeblock [->hash {:keys [ns graph record-missing-hash-fn]} {:as codeblock :keys [hash form id vars graph-node]}]
  (let [deps (when id (dep/immediate-dependencies graph id))
        hashed-deps (into (sorted-set) (keep ->hash) deps)]
    ;; NOTE: missing hashes on deps might occur e.g. when some dependencies are interned at runtime
    (when record-missing-hash-fn
      (when-some [dep-with-missing-hash
                  (some (fn [dep]
                          (when-not (get ->hash dep)
                            (when-not (deref? dep)          ;; on a first pass deref-nodes do not have a hash yet
                              dep))) deps)]
        (record-missing-hash-fn (assoc codeblock
                                       :dep-with-missing-hash dep-with-missing-hash
                                       :graph-node graph-node :ns ns))))
    (binding [*print-length* nil]
      (let [form-with-deps-sorted
            (-> hashed-deps
                (conj (if form
                        (-> form remove-type-meta canonicalize-form pr-str)
                        hash))
                (into (map str) vars))]
        (sha1-base58 (pr-str form-with-deps-sorted))))))

#_(hash-codeblock {} {:graph (dep/graph)} {})
#_(hash-codeblock {} {:graph (dep/graph)} {:hash "foo"})
#_(hash-codeblock {} {:graph (dep/graph)} {:id 'foo})
#_(hash-codeblock {'bar "dep-hash"} {:graph (dep/depend (dep/graph) 'foo 'bar)} {:id 'foo})

(defn hash
  ([{:as analyzed-doc :keys [graph]}] (hash analyzed-doc (dep/topo-sort graph)))
  ([{:as analyzed-doc :keys [->analysis-info]} deps]
   (update analyzed-doc
           :->hash
           (partial reduce (fn [->hash k]
                             (if-let [codeblock (get ->analysis-info k)]
                               (assoc ->hash k (hash-codeblock ->hash analyzed-doc (assoc codeblock :graph-node k)))
                               ->hash)))
           deps)))

#_(:time-ms
   (nextjournal.clerk.eval/time-ms
    (-> (parser/parse-file "src/nextjournal/clerk.clj")
        build-graph
        hash)))
#_(hash (build-graph (parser/parse-clojure-string "^{:nextjournal.clerk/hash-fn (fn [x] \"abc\")}(def contents (slurp \"notebooks/hello.clj\"))")))
#_(hash (build-graph (parser/parse-clojure-string (slurp "notebooks/hello.clj"))))

(defprotocol BoundedCountCheck
  (-exceeds-bounded-count-limit? [x limit]))

(extend-protocol BoundedCountCheck
  nil
  (-exceeds-bounded-count-limit? [_ _] false)
  Object
  (-exceeds-bounded-count-limit? [_ _] false)
  clojure.lang.IPersistentCollection
  (-exceeds-bounded-count-limit? [xs limit]
    (or (<= limit (bounded-count limit xs))
        (some #(-exceeds-bounded-count-limit? % limit) xs)
        false)))

(defn exceeds-bounded-count-limit? [x]
  (-exceeds-bounded-count-limit? x config/*bounded-count-limit*))

#_(time (exceeds-bounded-count-limit? viewers.table/letter->words))

(defn valuehash
  ([value] (valuehash :sha512 value))
  ([hash-type value]
   (let [digest-fn (case hash-type
                     :sha1 sha1-base58
                     :sha512 sha2-base58)]
     (utils/if-bb (-> value pr-str digest-fn)
                  #_{:clj-kondo/ignore [:unresolved-namespace]}
                  (binding [nippy/*incl-metadata?* false]
                    (-> value
                        nippy/fast-freeze
                        digest-fn))))))

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
