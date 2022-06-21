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
   [nextjournal.clerk.hashing :as hashing :refer [deflike?]]))

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

(defn deps
  [form]
  (-> form
      (hashing/analyze)
      (select-keys [:deps :vars :var :deref-deps])))

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
          @my-atom))
 )


