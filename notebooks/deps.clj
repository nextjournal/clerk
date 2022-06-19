^{:nextjournal.clerk/visibility :hide}
(ns deps
  (:require
   [clojure.tools.analyzer :as ana]
   [clojure.tools.analyzer.ast :as ana-ast]
   [clojure.tools.analyzer.jvm :as ana-jvm]
   [clojure.tools.analyzer.jvm.deps :as ana-deps]
   [clojure.tools.analyzer.passes.jvm.emit-form :as ana.passes.ef]
   [clojure.tools.analyzer.utils :as ana-utils]
   [nextjournal.clerk :as clerk]
   [nextjournal.clerk.hashing :refer [deflike?]]))

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
          analyzed (ana-jvm/analyze form (ana-jvm/empty-env)
                                    {:bindings {#'ana/macroexpand-1 mexpander}})
          vars (into #{}
                     (comp (filter (comp #{:def} :op))
                           (keep :var))
                     (ana-ast/nodes analyzed))
          var (when (and (= 1 (count vars))
                         (deflike? form))
                (first vars))]
      (cond-> {:deps @deps
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
 )
