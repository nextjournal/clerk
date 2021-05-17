f;; # Hashing Things!!!!
(ns nextjournal.clerk.hashing
  (:refer-clojure :exclude [hash])
  (:require [clojure.java.classpath :as cp]
            [clojure.set :as set]
            [clojure.string :as str]
            [clojure.tools.analyzer.jvm :as ana]
            [clojure.tools.analyzer.passes.jvm.emit-form :as ana.passes.ef]
            [datoteka.core :as fs]
            [rewrite-clj.parser :as p]
            [rewrite-clj.node :as n]
            [weavejester.dependency :as dep]))

(defn var-name
  "Takes a `form` and returns the name of the var, if it exists."
  [form]
  (when (and (sequential? form)
             (contains? '#{def defn} (first form)))
    (second form)))

(defn no-cache? [form]
  (-> form var-name meta :clerk/no-cache boolean))

(defn sha1-base64 [s]
  (String. (.encode (java.util.Base64/getUrlEncoder)
                    (.digest (java.security.MessageDigest/getInstance "SHA-1") (.getBytes s)))))

(comment
  (sha1-base64 "hello"))


(defn var-dependencies [form]
  (let [var-name (var-name form)]
    (->> form
         (tree-seq sequential? seq)
         (keep #(when (and (symbol? %)
                           (not= var-name %))
                  (resolve %)))
         (into #{}))))

#_(var-dependencies '(defn foo
                       ([] (foo "s"))
                       ([s] (str/includes? (p/parse-string-all s) "hi"))))

(defn analyze [form]
  (let [form (-> form
                 ana/analyze
                 (ana.passes.ef/emit-form #{:qualified-symbols}))
        var (some-> form var-name resolve)
        deps (cond-> (var-dependencies form) var (disj var))]
    (cond-> {:form (cond->> form var (drop 2))}
      var (assoc :var var)
      (seq deps) (assoc :deps deps))))

#_(analyze '(defn foo [s] (str/includes? (p/parse-string-all s) "hi")))
#_(analyze '(defn segments [s]
              (let [segments (str/split s)]
                (str/join segments))))


(defn remove-leading-semicolons [s]
  (str/replace s #"^[;]+" ""))


(defn parse-file
  ([file]
   (parse-file {} file))
  ([{:as _opts :keys [markdown?]} file]
   (loop [{:as state :keys [doc nodes]} {:nodes (:children (p/parse-file-all file))
                                         :doc []}]
     (if-let [node (first nodes)]
       (recur (cond
                (= :list (n/tag node)) (-> state
                                           (update :nodes rest)
                                           (update :doc (fnil conj []) {:type :code :text (n/string node)}))
                (and markdown? (n/comment? node)) (-> state
                                                      (assoc :nodes (drop-while n/comment? nodes))
                                                      (update :doc conj {:type :markdown :text (apply str (map (comp remove-leading-semicolons n/string)
                                                                                                               (take-while n/comment? nodes)))}))
                :else (update state :nodes rest)))
       doc))))

#_(parse-file "notebooks/elements.clj")
#_(parse-file {:markdown? true} "notebooks/elements.clj")


(defn analyze-file
  ([file]
   (analyze-file {} {:graph (dep/graph)} file))
  ([state file]
   (analyze-file {} state file))
  ([{:as opts :keys [markdown?]} acc file]
   (let [doc (parse-file opts file)]
     (reduce (fn [{:as acc :keys [graph]} {:keys [type text]}]
               (if (= type :code)
                 (let [form (read-string text)
                       _ (when (= 'ns (first form))
                           (eval form))
                       {:keys [var deps form]} (analyze form)]
                   (cond-> acc
                     var
                     (assoc-in [:var->hash var] {:file file
                                                 :form form
                                                 :deps deps})
                     (and var (seq deps))
                     (assoc :graph (reduce #(dep/depend %1 var %2) graph deps))))
                 acc))
             (cond-> acc markdown? (assoc :doc doc))
             doc))))

#_(:graph (analyze-file {:markdown? true} {:graph (dep/graph)} "notebooks/elements.clj"))


(defn unhashed-deps [var->hash]
  (set/difference (into #{}
                        (mapcat :deps)
                        (vals var->hash))
                  (-> var->hash keys set)))

#_(unhashed-deps {#'elements/fix-case {:deps #{#'rewrite-clj.node/tag}}})

;; TODO: handle cljc files
(defn ns->file [ns]
  (some (fn [dir]
          ;; TODO: fix case upstream when ns can be nil because var can contain java classes like java.lang.String
          (when-let [path (and ns (str dir fs/*sep* (str/replace ns "." fs/*sep*) ".clj"))]
            (when (fs/exists? path)
              path)))
        (cp/classpath-directories)))

#_(ns->file (find-ns 'nextjournal.clerk.hashing))

(def var->ns
  (comp :ns meta))

#_(var->ns #'inc)

(defn ns->jar [ns]
  (let [path (str (str/replace ns "." "/"))]
    (some #(when (or (.getJarEntry % (str path ".clj"))
                     (.getJarEntry % (str path ".cljc")))
             (.getName %))
          (cp/classpath-jarfiles))))

#_(ns->jar (var->ns #'dep/depend))

(defn symbol->jar [sym]
  (some-> (if (instance? Class sym)
            sym
            (class (cond-> sym (var? sym) deref)))
          .getProtectionDomain
          .getCodeSource
          .getLocation
          .getFile))

#_(symbol->jar com.mxgraph.view.mxGraph)
#_(symbol->jar #'inc)


(defn find-location [sym]
  (if (var? sym)
    (let [ns (var->ns sym)]
      (or (ns->file ns)
          (ns->jar ns)
          (symbol->jar sym)))
    (symbol->jar sym)))

#_(find-location #'inc)
#_(find-location #'dep/depend)
#_(find-location com.mxgraph.view.mxGraph)
#_(find-location String)

(defn build-graph
  "Analyzes the forms in the given file and builds a dependency graph of the vars.

  Recursively decends into depedency vars as well as given they can be found in the classpath.
  "
  [file]
  (let [{:as g :keys [var->hash]} (analyze-file file)]
    (reduce (fn [g [source symbols]]
              (if (or (nil? source)
                      (str/ends-with? source ".jar"))
                (update g :var->hash merge (into {} (map (juxt identity (constantly (if source {:jar source} {})))) symbols))
                (analyze-file g source)))
            g
            (group-by find-location (unhashed-deps var->hash)))))

#_(keys (:var->hash (build-graph "notebooks/elements.clj")))
#_(dep/immediate-dependencies (:graph (build-graph "notebooks/elements.clj"))  #'nextjournal.clerk.demo/fix-case)
#_(dep/transitive-dependencies (:graph (build-graph "notebooks/elements.clj"))  #'nextjournal.clerk.demo/fix-case)

#_(keys (:var->hash (build-graph "src/nextjournal/clerk/hashing.clj")))
#_(dep/topo-sort (:graph (build-graph "src/nextjournal/clerk/hashing.clj")))
#_(dep/immediate-dependencies (:graph (build-graph "src/nextjournal/clerk/hashing.clj"))  #'nextjournal.clerk.hashing/long-thing)
#_(dep/transitive-dependencies (:graph (build-graph "src/nextjournal/clerk/hashing.clj"))  #'nextjournal.clerk.hashing/long-thing)


(defn hash
  ([file]
   (let [{vars :var->hash :keys [graph]} (build-graph file)]
     (reduce (fn [vars->hash var]
               (if-let [info (get vars var)]
                 (assoc vars->hash var (hash vars->hash (assoc info :var var)))
                 vars->hash))
             {}
             (dep/topo-sort graph))))
  ([var->hash {:keys [jar form deps]}]
   (let [hashed-deps (into #{} (map var->hash) deps)]
     (sha1-base64 (pr-str (conj hashed-deps (if form form jar)))))))

#_(hash "notebooks/elements.clj")
#_(hash "src/nextjournal/clerk/hashing.clj")


;; (comment
;;   (declaring-classfiles clojure.core/add-tap)
;;   (declaring-classfiles io.methvin.watcher.DirectoryChangeEvent$EventType/CREATE)
;;   (declaring-classfiles nextjournal.directory-watcher/create))


;; (defn filenames-in-jar [path]
;;   (-> path
;;       io/file
;;       java.util.jar.JarFile.
;;       cp/filenames-in-jar
;;       set))

;; (comment
;;   (filenames-in-jar (nth (cp/classpath) 10))
;;   (filenames-in-jar (nth (cp/classpath) 16)))

;; (comment
;;   (-> (cp/classpath)
;;       (nth 10)
;;       io/file
;;       java.util.jar.JarFile.
;;       cp/filenames-in-jar
;;       set)

;;   (resolve 'io.methvin.watcher.DirectoryChangeEvent$EventType)
;;   ;; clojure var precompiled in jar

;;   (-> #'nextjournal.directory-watcher/create meta)
;;   (-> #'clojure.core/add-tap meta)

;;   (resolve 'io.methvin.watcher.DirectoryChangeEvent$EventType)
;;   (resolve 'io.methvin.watcher.DirectoryChangeEvent$EventType/CREATE)

;;   (reflect/reflect nextjournal.directory-watcher/create)
;;   (reflect/reflect clojure.core/add-tap)
;;   (find-source (type clojure.core/add-tap))
;;   ;; java class
;;   (find-source io.methvin.watcher.DirectoryChangeEvent$EventType/CREATE)
;;   ;; clojure var with source in jar TODO
;;   (find-source nextjournal.directory-watcher/create)
;;   )

;; (defn required-namespaces
;;   "Takes a `form` and returns a set of the namespaces it requires."
;;   [form]
;;   (->> form
;;        (tree-seq sequential? seq)
;;        (keep (fn [form]
;;                (when (and (sequential? form)
;;                           (= (first form) 'clojure.core/require))
;;                  (into #{}
;;                        (map #(let [f (second %)]
;;                                (cond-> f (sequential? f) first)))
;;                        (rest form)))))
;;        (apply set/union)))


;; (defn file-resource [var]
;;   (some-> var
;;           meta
;;           :file
;;           io/resource))
