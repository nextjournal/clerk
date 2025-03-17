;;   Copyright (c) Nicola Mometto, Rich Hickey & contributors.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;;   which can be found in the file epl-v10.html at the root of this distribution.
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.

(ns nextjournal.clerk.clojure.tools.analyzer.passes.jvm.box
  (:require [nextjournal.clerk.clojure.tools.analyzer.jvm.utils :as u]
            [clojure.tools.analyzer.utils :refer [protocol-node? arglist-for-arity]]
            [nextjournal.clerk.clojure.tools.analyzer.passes.jvm
             [validate :refer [validate]]
             [infer-tag :refer [infer-tag]]]))

(defmulti box
  "Box the AST node tag where necessary"
  {:pass-info {:walk :pre :depends #{#'infer-tag} :after #{#'validate}}}
  :op)

(defmacro if-let-box [class then else]
  `(let [c# ~class
         ~class (u/box c#)]
     (if (u/primitive? c#)
       ~then
       ~else)))

(defn -box [ast]
  (let [tag (:tag ast)]
    (if (u/primitive? tag)
      (assoc ast :tag (u/box tag))
      ast)))

(defn boxed? [tag expr]
  (and (or (nil? tag) (not (u/primitive? tag)))
       (u/primitive? (:tag expr))))

(defmethod box :instance-call
  [{:keys [args class validated? tag] :as ast}]
  (let [ast (if-let-box class
              (assoc (update-in ast [:instance :tag] u/box) :class class)
              ast)]
    (if validated?
      ast
      (assoc ast :args (mapv -box args)
             :o-tag Object :tag (if (not (#{Void Void/TYPE} tag))
                                  tag
                                  Object)))))

(defmethod box :static-call
  [{:keys [args validated? tag] :as ast}]
  (if validated?
    ast
    (assoc ast :args (mapv -box args)
           :o-tag Object :tag (if (not (#{Void Void/TYPE} tag))
                                tag
                                Object))))

(defmethod box :new
  [{:keys [args validated?] :as ast}]
  (if validated?
    ast
    (assoc ast :args (mapv -box args)
           :o-tag Object)))

(defmethod box :instance-field
  [{:keys [class] :as ast}]
  (if-let-box class
    (assoc (update-in ast [:instance :tag] u/box) :class class)
    ast))

(defmethod box :def
  [{:keys [init] :as ast}]
  (if (and init (u/primitive? (:tag init)))
    (update-in ast [:init] -box)
    ast))

(defmethod box :vector
  [ast]
  (assoc ast :items (mapv -box (:items ast))))

(defmethod box :set
  [ast]
  (assoc ast :items (mapv -box (:items ast))))

(defmethod box :map
  [ast]
  (let [keys (mapv -box (:keys ast))
        vals (mapv -box (:vals ast))]
    (assoc ast
      :keys keys
      :vals vals)))

(defmethod box :do
  [ast]
  (if (boxed? (:tag ast) (:ret ast))
    (-> ast
      (update-in [:ret] -box)
      (update-in [:o-tag] u/box))
    ast))

(defmethod box :quote
  [ast]
  (if (boxed? (:tag ast) (:ret ast))
    (-> ast
      (update-in [:expr] -box)
      (update-in [:o-tag] u/box))
    ast))

(defmethod box :protocol-invoke
  [ast]
  (assoc ast :args (mapv -box (:args ast))))

(defmethod box :let
  [{:keys [tag body] :as ast}]
  (if (boxed? tag body)
    (-> ast
      (update-in [:body] -box)
      (update-in [:o-tag] u/box))
    ast))

(defmethod box :letfn
  [ast]
  (if (boxed? (:tag ast) (:body ast))
    (-> ast
      (update-in [:body] -box)
      (update-in [:o-tag] u/box))
    ast))

(defmethod box :loop
  [ast]
  (if (boxed? (:tag ast) (:body ast))
    (-> ast
      (update-in [:body] -box)
      (update-in [:o-tag] u/box))
    ast))

(defmethod box :fn-method
  [{:keys [params tag] :as  ast}]
  (let [ast (if (u/primitive? tag)
              ast
              (-> ast
                (update-in [:body] -box)
                (update-in [:o-tag] u/box)))]
    (assoc ast
      :params (mapv (fn [{:keys [o-tag] :as p}]
                      (assoc p :o-tag (u/prim-or-obj o-tag))) params)
      :tag   (u/prim-or-obj tag)
      :o-tag (u/prim-or-obj tag))))

(defmethod box :if
  [{:keys [test then else tag o-tag] :as ast}]
  (let [test-tag (:tag test)
        test (if (and (u/primitive? test-tag)
                      (not= Boolean/TYPE test-tag))
               (assoc test :tag (u/box test-tag))
               test)
        [then else o-tag] (if (or (boxed? tag then)
                                  (boxed? tag else)
                                  (not o-tag))
                            (conj (mapv -box [then else]) (u/box o-tag))
                            [then else o-tag])]
    (merge ast
           {:test  test
            :o-tag o-tag
            :then  then
            :else  else})))

(defmethod box :case
  [{:keys [tag default tests thens test-type] :as ast}]
  (let [ast (if (and tag (u/primitive? tag))
              ast
              (-> ast
                (assoc-in [:thens] (mapv (fn [t] (update-in t [:then] -box)) thens))
                (update-in [:default] -box)
                (update-in [:o-tag] u/box)))]
    (if (= :hash-equiv test-type)
      (-> ast
        (update-in [:test] -box)
        (assoc-in [:tests] (mapv (fn [t] (update-in t [:test] -box)) tests)))
      ast)))

(defmethod box :try
  [{:keys [tag] :as ast}]
  (let [ast (if (and tag (u/primitive? tag))
              ast
              (-> ast
                (update-in [:catches] #(mapv -box %))
                (update-in [:body] -box)
                (update-in [:o-tag] u/box)))]
    (-> ast
      (update-in [:finally] -box))))

(defmethod box :invoke
  [ast]
  (assoc ast
    :args  (mapv -box (:args ast))
    :o-tag Object))

(defmethod box :default [ast] ast)
