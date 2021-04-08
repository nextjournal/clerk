;; # Hashing Things!!!!
(ns observator.hashing
  (:refer-clojure :exclude [hash])
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [clojure.tools.analyzer.jvm :as ana]
            [clojure.tools.analyzer.passes.jvm.emit-form :as ana.passes.ef]
            [datoteka.core :as fs]
            [observator.lib :as obs.lib]
            [rewrite-clj.parser :as p]
            [rewrite-clj.node :as n]
            [weavejester.dependency :as dep]))

(def long-thing
  (map obs.lib/fix-case (str/split-lines (slurp "/usr/share/dict/words"))))

(take 3 long-thing)


(defn var-name
  "Takes a `form` and returns the name of the var, if it exists."
  [form]
  (when (and (sequential? form)
             (contains? '#{def defn} (first form)))
    (second form)))


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
                       ([s] (str/includes? (obs.lib/fix-case s) "hi"))))

(defn analyze [form]
  (let [form (-> form
                 ana/analyze
                 (ana.passes.ef/emit-form #{:qualified-symbols}))
        var (some-> form var-name resolve)
        deps (var-dependencies form)]
    (cond-> {:form (cond->> form var (drop 2))}
      var (assoc :var var)
      (seq deps) (assoc :deps deps))))

#_(analyze '(defn foo [s] (str/includes? (obs.lib/fix-case s) "hi")))


(defn build-graph
  "Analyzes the forms in the given file and builds a dependency graph of the vars.

  Recursively decends into depedency vars as well as given they can be found in the classpath.

  Future improvements:
  * Also analyze files found in jars
  * Handle cljc files
  * Handle compiled java code
  "
  ([file]
   (build-graph {:graph (dep/graph) :var->hash {} :visited #{}} file))
  ([{:as g :keys [_graph _var->hash _visited]} file]
   (let [{:as g :keys [_graph var->hash visited]}
         (loop [{:keys [graph nodes] :as g} (assoc g :nodes (:children (p/parse-string-all (slurp file))))]
           (if-let [node (first nodes)]
             (recur (cond
                      (= :list (n/tag node)) (let [form (-> node n/string read-string)
                                                   _ (when (= "ns" (-> node n/children first n/string))
                                                       (eval form))
                                                   {:keys [var deps form]} (analyze form)]
                                               (cond-> (update g :nodes rest)
                                                 var
                                                 (assoc-in [:var->hash var] {:file file
                                                                             :form form
                                                                             :deps deps})
                                                 (and var (seq deps))
                                                 (assoc :graph (reduce #(dep/depend %1 var %2) graph deps))))
                      :else (update g :nodes rest)))
             (dissoc g :nodes)))

         visited
         (set/union visited (into #{} (map (comp :ns meta)) (keys var->hash)))

         deps
         (set/difference (into #{}
                               (comp (mapcat :deps)
                                     (filter (fn [var] (not (str/starts-with? (-> var meta :ns str) "clojure.")))))
                               (vals var->hash))
                         (-> var->hash keys set))

         deps-ns
         (into #{} (comp (filter (complement visited))
                         (map (comp :ns meta))) deps)

         files-to-hash
         (into #{}
               (comp (map #(str "src/" (str/replace % "." fs/*sep*) ".clj"))
                     (filter fs/exists?))
               (set/difference deps-ns visited))]
     (reduce #(build-graph %1 %2) (assoc g :var->hash var->hash :visited visited) files-to-hash))))

#_(keys (build-graph "src/observator/demo.clj"))
#_(dep/topo-sort (:graph (build-graph "src/observator/demo.clj")))
#_(dep/immediate-dependencies (:graph (build-graph "src/observator/demo.clj")) #'observator.demo/fix-case)
#_(dep/transitive-dependencies (:graph (build-graph "src/observator/demo.clj")) #'observator.demo/fix-case)


(defn hash
  ([file]
   (let [{vars :var->hash :keys [graph]} (build-graph file)]
     (reduce (fn [vars->hash var]
               (if-let [info (get vars var)]
                 (assoc vars->hash var (hash vars->hash (assoc info :var var)))
                 vars->hash))
             {}
             (dep/topo-sort graph))))
  ([var->hash {:keys [form deps]}]
   (let [hashed-deps (into #{} (map var->hash) deps)]
     (sha1-base64 (pr-str (conj hashed-deps form))))))

#_(hash "src/observator/demo.clj")


;; (defn declaring-classfiles [sym]
;;   (->> sym
;;        reflect/reflect
;;        :members
;;        (map :declaring-class)
;;        (map #(str (str/replace % "." fs/*sep*) ".class"))
;;        set))

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
;;   (-> #'observator.lib/fix-case meta)
;;   (-> #'observator.core/fix-case meta)
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


;; (comment
;;   (meta (find-ns 'observator.demo))

;;   (required-namespaces '(ns observator.core
;;                           (:require [observator.lib :as obs.lib]
;;                                     observator.demo)))

;;   (required-namespaces '(do (require '[observator.lib :as obs.lib]
;;                                      'observator.demo2)
;;                             (require 'observator.demo)))
;;   ;; TODO
;;   (required-namespaces '(require '(clojure zip [set :as s]))))

;; (defn file-resource [var]
;;   (some-> var
;;           meta
;;           :file
;;           io/resource))
