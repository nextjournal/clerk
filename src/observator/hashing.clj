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
            [rewrite-clj.node :as n]))

(def long-thing
  (map obs.lib/fix-case (str/split-lines (slurp "/usr/share/dict/words"))))

(take 3 long-thing)


(defn analyze+qualify [form]
  (-> form
      ana/analyze
      (ana.passes.ef/emit-form #{:qualified-symbols})))

#_(analyze+qualify 'analyze+qualify)

(defn sha1-base64 [s]
  (String. (.encode (java.util.Base64/getUrlEncoder)
                    (.digest (java.security.MessageDigest/getInstance "SHA-1") (.getBytes s)))))

(comment
  (sha1-base64 "hello"))

(defn var-name
  "Takes a `form` and returns the name of the var, if it exists."
  [form]
  (when (and (sequential? form)
             (contains? '#{def defn} (first form)))
    (second form)))

#_(var-name '(def foo :bar))

(defn var-dependencies [form]
  (let [form (analyze+qualify form)
        var-name (var-name form)]
    (->> form
         (tree-seq sequential? seq)
         (keep #(when (and (symbol? %)
                           (not= var-name %))
                  (resolve %)))
         (into #{}))))

#_(var-dependencies '(defn foo
                       ([] (foo "s"))
                       ([s] (str/includes? (obs.lib/fix-case s) "hi"))))

(defn +deps
  [form]
  (let [form (cond->> form
               (var-name form) (drop 2))]
    {:form form
     :deps (var-dependencies form)}))

(defn hash-vars
  "Hashes the defined vars found in the file (normally a namespace).

  Recursively hashes depedency vars as well as given they can be found
  in the classpath."
  ([file]
   (hash-vars {} file))
  ([var->hash file]
   (hash-vars var->hash #{} file))
  ([var->hash visited file]
   (let [var->hash
         (loop [{:keys [var->hash nodes] :as r} {:nodes (:children (p/parse-string-all (slurp file)))
                                                 :visited #{}
                                                 :var->hash var->hash}]
           (if-let [node (first nodes)]
             (recur (cond
                      (= :list (n/tag node)) (let [form (-> node n/string read-string)
                                                   _ (when (= "ns" (-> node n/children first n/string))
                                                       (eval form))
                                                   form (analyze+qualify form)
                                                   var-name (var-name form)]
                                               (cond-> (update r :nodes rest)
                                                 var-name
                                                 (assoc-in [:var->hash var-name] (+deps form))))
                      :else (update r :nodes rest)))
             var->hash))

         visited
         (set/union visited (into #{} (map (comp :ns meta resolve)) (keys var->hash)))

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
     (reduce #(hash-vars %1 visited %2) var->hash files-to-hash))))

#_(keys (hash-vars "src/observator/demo.clj"))


(defn hash
  [var->hash form]
  (sha1-base64
   (pr-str (conj (into #{} (map var->hash) (var-dependencies form))
                 (cond->> form
                   (var-name form) (drop 2))))))



(comment
  (hash {} '(def foo observator.lib/fix-case)))

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
