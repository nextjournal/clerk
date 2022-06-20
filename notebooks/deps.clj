^{:nextjournal.clerk/visibility :hide}
(ns deps
  (:require
   [clojure.set :as set]
   [clojure.tools.analyzer :as ana]
   [clojure.tools.analyzer.ast :as ana-ast]
   [clojure.tools.analyzer.jvm :as ana-jvm]
   [clojure.tools.analyzer.passes.jvm.emit-form :as ana.passes.ef]
   [clojure.tools.analyzer.utils :as ana-utils]
   [nextjournal.clerk :as clerk]
   [nextjournal.clerk.hashing :refer [deflike?]]))

(defn analyze
  ([form] (analyze {} form))
  ([bindings form]
   (ana-jvm/analyze form (ana-jvm/empty-env) {:bindings bindings})))

(defn deps
  [form]
  (if (var? form)
    #{form}
    (let [deps      (atom #{})
          mexpander (fn [form env]
                      (let [f (if (seq? form) (first form) form)
                            v (ana-utils/resolve-sym f env)]
                        (when-let [var? (and (not (-> env :locals (get f)))
                                             (var? v))]
                          (swap! deps conj v)))
                      (ana-jvm/macroexpand-1 form env))
          analyzed (analyze {#'ana/macroexpand-1 mexpander} form)
          nodes (ana-ast/nodes analyzed)
          vars (into #{}
                     (comp (filter (comp #{:def} :op))
                           (keep :var))
                     nodes)
          var (when (and (= 1 (count vars))
                         (deflike? form))
                (first vars))]
      (cond-> {:deps (set/union @deps)
               :deref-deps (into #{}
                                 (comp (filter (comp #{#'deref} :var :fn))
                                       (keep #(-> % :args first :var)))
                                 nodes)
               :vars vars}
        var (assoc :var var)))))


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
 )
