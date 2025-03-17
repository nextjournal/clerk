;;   Copyright (c) Nicola Mometto, Rich Hickey & contributors.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;;   which can be found in the file epl-v10.html at the root of this distribution.
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.

(ns nextjournal.clerk.clojure.tools.analyzer.passes.jvm.classify-invoke
  (:require [clojure.tools.analyzer.utils :refer [arglist-for-arity protocol-node? source-info]]
            [nextjournal.clerk.clojure.tools.analyzer.jvm.utils
             :refer [specials prim-interface]]
            [nextjournal.clerk.clojure.tools.analyzer.passes.jvm.validate :refer [validate]]))

(defn classify-invoke
  "If the AST node is an :invoke, check the node in function position,
   * if it is a keyword, transform the node in a :keyword-invoke node;
   * if it is the clojure.core/instance? var and the first argument is a
     literal class, transform the node in a :instance? node to be inlined by
     the emitter
   * if it is a protocol function var, transform the node in a :protocol-invoke
     node
   * if it is a regular function with primitive type hints that match a
     clojure.lang.IFn$[primitive interface], transform the node in a :prim-invoke
     node"
  {:pass-info {:walk :post :depends #{#'validate}}}
  [{:keys [op args tag env form] :as ast}]
  (if-not (= op :invoke)
    ast
    (let [argc (count args)
          the-fn (:fn ast)
          op (:op the-fn)
          var? (= :var op)
          the-var (:var the-fn)]

      (cond

       (and (= :const op)
            (= :keyword (:type the-fn)))
       (if (<= 1 argc 2)
         (if (and (not (namespace (:val the-fn)))
                  (= 1 argc))
           (merge (dissoc ast :fn :args)
                  {:op       :keyword-invoke
                   :target   (first args)
                   :keyword  the-fn
                   :children [:keyword :target]})
           ast)
         (throw (ex-info (str "Cannot invoke keyword with " argc " arguments")
                         (merge {:form form}
                                (source-info env)))))
       (and (= 2 argc)
            var?
            (= #'clojure.core/instance? the-var)
            (= :const (:op (first args)))
            (= :class (:type (first args))))
       (merge (dissoc ast :fn :args)
              {:op       :instance?
               :class    (:val (first args))
               :target   (second args)
               :form     form
               :env      env
               :o-tag    Boolean/TYPE
               :tag      (or tag Boolean/TYPE)
               :children [:target]})

       (and var? (protocol-node? the-var (:meta the-fn)))
       (if (>= argc 1)
         (merge (dissoc ast :fn)
                {:op          :protocol-invoke
                 :protocol-fn the-fn
                 :target      (first args)
                 :args        (vec (rest args))
                 :children    [:protocol-fn :target :args]})
         (throw (ex-info "Cannot invoke protocol method with no args"
                         (merge {:form form}
                                (source-info env)))))

       :else
       (let [arglist (arglist-for-arity the-fn argc)
             arg-tags (mapv (comp specials str :tag meta) arglist)
             ret-tag (-> arglist meta :tag str specials)
             tags (conj arg-tags ret-tag)]
         (if-let [prim-interface (prim-interface (mapv #(if (nil? %) Object %) tags))]
           (merge ast
                  {:op             :prim-invoke
                   :prim-interface prim-interface
                   :args           (mapv (fn [arg tag] (assoc arg :tag tag)) args arg-tags)
                   :o-tag          ret-tag
                   :tag            (or tag ret-tag)})
           ast))))))
