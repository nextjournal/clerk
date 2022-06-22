^{:nextjournal.clerk/visibility :hide}
(ns deps
  {:nextjournal.clerk/no-cache true}
  (:require
   [clojure.set :as set]
   [clojure.tools.analyzer :as ana]
   [clojure.tools.analyzer.ast :as ana-ast]
   [clojure.tools.analyzer.jvm :as ana-jvm]
   [clojure.tools.analyzer.passes.jvm.emit-form :as ana.passes.ef]
   [clojure.tools.analyzer.utils :as ana-utils]
   [nextjournal.clerk :as clerk]
   [nextjournal.clerk.hashing :as hashing :refer [deflike? class-dependencies no-cache?]]))

(defn- analyze-form
  ([form] (analyze-form {} form))
  ([bindings form]
   (ana-jvm/analyze form (ana-jvm/empty-env) {:bindings bindings})))

(defn- nodes-outside-of-fn
  "Like `clojure.tools.anayzer.ast/nodes` but does not descend into children of `:fn` nodes."
  [ast]
  (lazy-seq
   (when-not (-> ast :op #{:fn})
     (cons ast (mapcat nodes-outside-of-fn (ana-ast/children ast))))))

#_(defn class-dependencies [form]
    (into #{}
          (filter #(and (symbol? %)
                        (if (qualified-symbol? %)
                          (-> % namespace symbol resolve class?)
                          (and (not= \. (-> % str (.charAt 0)))
                               (-> % resolve class?)))))
          (tree-seq (every-pred (some-fn sequential? map? set?) not-quoted?) seq form)))

(defn analyze [form]
  (if (var? form)
    #{form}
    (let [!deps      (atom #{})
          mexpander (fn [form env]
                      (let [f (if (seq? form) (first form) form)
                            v (ana-utils/resolve-sym f env)]
                        (when-let [var? (and (not (-> env :locals (get f)))
                                             (var? v))]
                          (swap! !deps conj v)))
                      (ana-jvm/macroexpand-1 form env))
          analyzed (analyze-form {#'ana/macroexpand-1 mexpander} form)
          nodes (ana-ast/nodes analyzed)
          vars (into #{}
                     (comp (filter (comp #{:def} :op))
                           (keep :var)
                           (map symbol))
                     nodes)
          var (when (and (= 1 (count vars))
                         (deflike? form))
                (first vars))
          deref-deps (into #{}
                           (comp (filter (comp #{#'deref} :var :fn))
                                 (keep #(-> % :args first :var))
                                 (map #(list `deref (symbol %))))
                           (nodes-outside-of-fn analyzed))
          deps (set/union (set/difference (into #{} (map symbol) @!deps) vars)
                          deref-deps
                          (class-dependencies form))
          hash-fn (-> form meta :nextjournal.clerk/hash-fn)]
      (cond-> {:form form
               :analyzed analyzed
               :ns-effect? (some? (some #{'clojure.core/require 'clojure.core/in-ns} deps))
               :freezable? (and (not (some #{'clojure.core/intern} deps))
                                (<= (count vars) 1)
                                (if (seq vars) (= var (first vars)) true))
               :no-cache? (no-cache? form)}
        hash-fn (assoc :hash-fn hash-fn)
        (seq deps) (assoc :deps deps)
        (seq deref-deps) (assoc :deref-deps deref-deps)
        (seq vars) (assoc :vars vars)
        var (assoc :var var)))))

(analyze '#{io.methvin.watcher.hashing.FileHasher/DEFAULT_FILE_HASHER})

(defn deps
  [form]
  (-> form
      analyze
      #_
      (select-keys [:deps :vars :var :deref-deps])))


^::clerk/no-cache
(clerk/example
 (deps '(import javax.imageio.ImageIO))
 (deps '#{io.methvin.watcher.hashing.FileHasher/DEFAULT_FILE_HASHER}))

#_
^::clerk/no-cache
(clerk/example
 (deps '(def foo :bar))
 (deps '(defn my-inc [x] (inc x)))
 (deps '(defn my-inc [x] (let [inc (partial + 1)]
                           (inc x))))
 (deps '(defn my-inc' [x] (my-inc x)))
 (deps '(import javax.imageio.ImageIO))
 (deps '(defmulti foo :bar))
 (deps '(defonce !counter (atom 0)))
 (deps '@!counter)
 (deps '(deref !counter))
 (deps '(import javax.imageio.ImageIO))
 (deps '(do 'inc))
 (deps '(comment @!counter))
 (deps '(fn [] @!counter))
 (deps '(inc @!counter))
 (deps '(let [my-atom (atom 0)]
          (swap! my-atom inc)
          @my-atom)))



