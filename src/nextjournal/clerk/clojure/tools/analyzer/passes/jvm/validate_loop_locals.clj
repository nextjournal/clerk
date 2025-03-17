;;   Copyright (c) Nicola Mometto, Rich Hickey & contributors.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;;   which can be found in the file epl-v10.html at the root of this distribution.
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.

(ns nextjournal.clerk.clojure.tools.analyzer.passes.jvm.validate-loop-locals
  (:require [clojure.tools.analyzer.ast :refer [postwalk children update-children]]
            [nextjournal.clerk.clojure.tools.analyzer.jvm.utils :refer [wider-tag maybe-class primitive?]]
            [nextjournal.clerk.clojure.tools.analyzer.passes.jvm
             [validate :refer [validate]]
             [classify-invoke :refer [classify-invoke]]
             [infer-tag :refer [infer-tag]]
             [analyze-host-expr :refer [analyze-host-expr]]]))

(def ^:dynamic ^:private validating nil)
(def ^:dynamic ^:private mismatch?)
(def ^:dynamic ^:private *loop-locals* [])

(defn find-mismatches [{:keys [op exprs] :as ast} bindings]
  (case op
    :recur
    (when (some true? (mapv (fn [e {:keys [tag init form]}]
                           (and (or (primitive? tag)
                                    (not (or (:tag (meta form))
                                           (:tag (meta (:form init))))))
                                (not= (:tag e) tag))) exprs bindings))
      (swap! mismatch? conj (mapv :tag exprs)))
    :do
    (doseq [child (children ast)]
      (find-mismatches child bindings))
    (:let :letfn)
    (find-mismatches (:body ast) bindings)
    :if
    (do (find-mismatches (:then ast) bindings)
        (find-mismatches (:else ast) bindings))
    :case
    (do (find-mismatches (:default ast) bindings)
        (doseq [child (:thens ast)]
          (find-mismatches child bindings)))
    nil)
  ast)

(defmulti -validate-loop-locals (fn [_ {:keys [op]}] op))
(defmulti -cleanup-dirty-nodes :op)

(defmethod -cleanup-dirty-nodes :local
  [{:keys [form name atom env] :as ast}]
  (if-let [cast ((:loop-locals-casts env) name)]
    (assoc ast
      :dirty? true
      :o-tag cast
      :tag (or (:tag (meta form)) cast))
    (if (and (:dirty? @atom)
             (not (:tag (meta form))))
      (dissoc (assoc ast :dirty? true) :o-tag :tag)
      ast)))

(defn dirty [ast]
  (when-let [atom (:atom ast)]
    (swap! atom assoc :dirty? true))
  (assoc (update-children ast (fn [ast] (dissoc ast :dirty?)))
    :dirty? true))

(defmethod -cleanup-dirty-nodes :do
  [{:keys [op ret] :as ast}]
  (if (:dirty? ret)
    (dissoc (dirty ast) :tag)
    ast))

;; should check for :tag meta form
(defmethod -cleanup-dirty-nodes :default
  [{:keys [op] :as ast}]
  (if (some :dirty? (children ast))
    (dissoc (dirty ast)
            :tag :validated? (when (= :instance-call op) :class))
    ast))

(defn -validate-loop-locals*
  [analyze {:keys [body env loop-id] :as ast} key]
  (if validating
    ast
    (binding [mismatch? (atom #{})]
      (let [bindings (key ast)]
        (find-mismatches body bindings)
        (if-let [mismatches (seq @mismatch?)]
          (let [bindings-form (apply mapv
                                     (fn [{:keys [form tag]} & mismatches]
                                       (when-not (every? #{tag} mismatches)
                                         (let [tags (conj mismatches tag)]
                                           (with-meta form {:tag (or (and (some primitive? tags)
                                                                          (wider-tag tags))
                                                                     Object)}))))
                                     bindings mismatches)
                loop-locals (mapv :name bindings)
                binds (zipmap loop-locals (mapv (comp maybe-class :tag meta) bindings-form))
                analyze* (fn [ast]
                           (analyze (postwalk ast
                                              (fn [ast]
                                                (when-let [atom (:atom ast)]
                                                  (swap! atom dissoc :dirty?))
                                                ast))))]
            (binding [validating    loop-id
                      *loop-locals* loop-locals]
              (analyze* (dissoc (postwalk (assoc ast key
                                                 (mapv (fn [{:keys [atom] :as bind} f]
                                                         (if f
                                                           (do
                                                             (swap! atom assoc :dirty? true)
                                                             (assoc (dissoc bind :tag) :form f))
                                                           bind))
                                                       (key ast) bindings-form))
                                          (comp -cleanup-dirty-nodes
                                             (fn [ast] (assoc-in ast [:env :loop-locals-casts] binds))))
                                :dirty?))))
          ast)))))

(defmethod -validate-loop-locals :loop
  [analyze ast]
  (-validate-loop-locals* analyze ast :bindings))

(defmethod -validate-loop-locals :fn-method
  [analyze ast]
  (-validate-loop-locals* analyze ast :params))

(defmethod -validate-loop-locals :method
  [analyze ast]
  (-validate-loop-locals* analyze ast :params))

(defmethod -validate-loop-locals :recur
  [_ {:keys [exprs env loop-id] :as ast}]
  (if (= validating loop-id)
    (let [casts (:loop-locals-casts env)]
      (assoc ast
        :exprs (mapv (fn [{:keys [env form] :as e} n]
                       (if-let [c (casts n)]
                         (assoc e :tag c)
                         e)) exprs *loop-locals*)))
    ast))

(defmethod -validate-loop-locals :default
  [_ ast]
  ast)

(defn validate-loop-locals
  "Returns a pass that validates the loop locals, calling analyze on the loop AST when
   a mismatched loop-local is found"
  {:pass-info {:walk :post :depends #{#'validate} :affects #{#'analyze-host-expr #'infer-tag #'validate} :after #{#'classify-invoke}}}
  [analyze]
  (fn [ast] (-validate-loop-locals analyze ast)))
