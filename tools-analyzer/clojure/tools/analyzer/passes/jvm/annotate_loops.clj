;;   Copyright (c) Nicola Mometto, Rich Hickey & contributors.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;;   which can be found in the file epl-v10.html at the root of this distribution.
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.

(ns nextjournal.clerk.clojure.tools.analyzer.passes.jvm.annotate-loops
  (:require [clojure.tools.analyzer.ast :refer [update-children]]))

(defmulti annotate-loops
  "Adds a :loops field to nodes that represent a code path that
   might be visited more than once because of a recur.

   The field is a set of loop-ids representing the loops that might
   recur into that path

   Note that because (recur expr) is equivalent to (let [e expr] (recur e))
   the node corresponting to expr will have the same :loops field
   as the nodes in the same code path of the recur"
  {:pass-info {:walk :pre :depends #{}}}
  :op)

(defmulti check-recur :op)

(defn -check-recur [ast k]
  (let [ast (update-in ast [k] check-recur)]
    (if (:recurs (k ast))
      (assoc ast :recurs true)
      ast)))

(defmethod check-recur :do
  [ast]
  (let [ast (-check-recur ast :ret)]
    (if (:recurs ast)
      (assoc ast :statements (mapv (fn [s] (assoc s :recurs true)) (:statements ast)))
      ast)))

(defmethod check-recur :let
  [ast]
  (-check-recur ast :body))

(defmethod check-recur :letfn
  [ast]
  (-check-recur ast :body))

(defmethod check-recur :if
  [ast]
  (-> ast
    (-check-recur :then)
    (-check-recur :else)))

(defmethod check-recur :case
  [ast]
  (let [ast (-> ast
                (-check-recur :default)
                (update-in [:thens] #(mapv check-recur %)))]
    (if (some :recurs (:thens ast))
      (assoc ast :recurs true)
      ast)))

(defmethod check-recur :case-then
  [ast]
  (-check-recur ast :then))

(defmethod check-recur :recur
  [ast]
  (assoc ast :recurs true))

(defmethod check-recur :default
  [ast]
  ast)

(defn -loops [ast loop-id]
  (update-in ast [:loops] (fnil conj #{}) loop-id))

(defmethod annotate-loops :loop
  [{:keys [loops loop-id] :as ast}]
  (let [ast (if loops
              (update-children ast #(assoc % :loops loops))
              ast)
        ast (update-in ast [:body] check-recur)]
    (if (-> ast :body :recurs)
      (update-in ast [:body] -loops loop-id)
      ast)))

(defmethod annotate-loops :default
  [{:keys [loops] :as ast}]
  (if loops
    (update-children ast #(assoc % :loops loops))
    ast))

(defmethod annotate-loops :if
  [{:keys [loops test then else env] :as ast}]
  (if loops
    (let [loop-id (:loop-id env)
          loops-no-recur (disj loops loop-id)
          branch-recurs? (or (:recurs then) (:recurs else))
          then (if (or (:recurs then) ;; the recur is inside the then branch
                       ;; the recur is in the same code path of the if expression
                       (not branch-recurs?))
                 (assoc then :loops loops)
                 (assoc then :loops loops-no-recur))
          else (if (or (:recurs else) (not branch-recurs?))
                 (assoc else :loops loops)
                 (assoc else :loops loops-no-recur))]
      (assoc ast
        :then then
        :else else
        :test (assoc test :loops loops)))
    ast))

(defmethod annotate-loops :case
  [{:keys [loops test default thens env] :as ast}]
  (if loops
    (let [loop-id (:loop-id env)
          loops-no-recur (disj loops loop-id)
          branch-recurs? (some :recurs (conj thens default))

          default (if (or (:recurs default) (not branch-recurs?))
                    (assoc default :loops loops)
                    (assoc default :loops loops-no-recur))

          thens (mapv #(if (or (:recurs %) (not branch-recurs?))
                         (assoc % :loops loops)
                         (assoc % :loops loops-no-recur)) thens)]
      (assoc ast
        :thens   thens
        :default default
        :test    (assoc test :loops loops)))
    ast))
