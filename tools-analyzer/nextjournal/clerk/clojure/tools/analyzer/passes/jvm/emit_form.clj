;;   Copyright (c) Nicola Mometto, Rich Hickey & contributors.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;;   which can be found in the file epl-v10.html at the root of this distribution.
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.

(ns nextjournal.clerk.clojure.tools.analyzer.passes.jvm.emit-form
  (:require [clojure.tools.analyzer.passes
             [emit-form :as default]
             [uniquify :refer [uniquify-locals]]]))

(defmulti -emit-form (fn [{:keys [op]} _] op))

(defn -emit-form*
  [{:keys [form] :as ast} opts]
  (let [expr (-emit-form ast opts)]
    (if-let [m (and (instance? clojure.lang.IObj expr)
                    (meta form))]
      (with-meta expr (merge m (meta expr)))
      expr)))

;; TODO: use pass opts infr
(defn emit-form
  "Return the form represented by the given AST
   Opts is a set of options, valid options are:
    * :hygienic
    * :qualified-vars (DEPRECATED, use :qualified-symbols instead)
    * :qualified-symbols"
  {:pass-info {:walk :none :depends #{#'uniquify-locals} :compiler true}}
  ([ast] (emit-form ast #{}))
  ([ast opts]
     (binding [default/-emit-form* -emit-form*]
       (-emit-form* ast opts))))

(defn emit-hygienic-form
  "Return an hygienic form represented by the given AST"
  {:pass-info {:walk :none :depends #{#'uniquify-locals} :compiler true}}
  [ast]
  (binding [default/-emit-form* -emit-form*]
    (-emit-form* ast #{:hygienic})))

(defmethod -emit-form :default
  [ast opts]
  (default/-emit-form ast opts))

(defmethod -emit-form :const
  [{:keys [type val] :as ast} opts]
  (if (and (= type :class)
           (:qualified-symbols opts))
    (symbol (.getName ^Class val))
    (default/-emit-form ast opts)))

(defmethod -emit-form :monitor-enter
  [{:keys [target]} opts]
  `(monitor-enter ~(-emit-form* target opts)))

(defmethod -emit-form :monitor-exit
  [{:keys [target]} opts]
  `(monitor-exit ~(-emit-form* target opts)))

(defmethod -emit-form :import
  [{:keys [class]} opts]
  `(clojure.core/import* ~class))

(defmethod -emit-form :the-var
  [{:keys [^clojure.lang.Var var]} opts]
  `(var ~(symbol (name (ns-name (.ns var))) (name (.sym var)))))

(defmethod -emit-form :method
  [{:keys [params body this name form]} opts]
  (let [params (into [this] params)]
    `(~(with-meta name (meta (first form)))
      ~(with-meta (mapv #(-emit-form* % opts) params)
         (meta (second form)))
      ~(-emit-form* body opts))))

(defn class->str [class]
  (if (symbol? class)
    (name class)
    (.getName ^Class class)))

(defn class->sym [class]
  (if (symbol? class)
    class
    (symbol (.getName ^Class class))))

(defmethod -emit-form :catch
  [{:keys [class local body]} opts]
  `(catch ~(-emit-form* class opts) ~(-emit-form* local opts)
     ~(-emit-form* body opts)))

(defmethod -emit-form :deftype
  [{:keys [name class-name fields interfaces methods]} opts]
  `(deftype* ~name ~(class->sym class-name) ~(mapv #(-emit-form* % opts) fields)
     :implements ~(mapv class->sym interfaces)
     ~@(mapv #(-emit-form* % opts) methods)))

(defmethod -emit-form :reify
  [{:keys [interfaces methods]} opts]
  `(reify* ~(mapv class->sym (disj interfaces clojure.lang.IObj))
           ~@(mapv #(-emit-form* % opts) methods)))

(defmethod -emit-form :case
  [{:keys [test default tests thens shift mask low high switch-type test-type skip-check?]} opts]
  `(case* ~(-emit-form* test opts)
          ~shift ~mask
          ~(-emit-form* default opts)
          ~(apply sorted-map
                  (mapcat (fn [{:keys [hash test]} {:keys [then]}]
                            [hash [(-emit-form* test opts) (-emit-form* then opts)]])
                          tests thens))
          ~switch-type ~test-type ~skip-check?))

(defmethod -emit-form :static-field
  [{:keys [class field]} opts]
  (symbol (class->str class) (name field)))

(defmethod -emit-form :static-call
  [{:keys [class method args]} opts]
  `(~(symbol (class->str class) (name method))
    ~@(mapv #(-emit-form* % opts) args)))

(defmethod -emit-form :instance-field
  [{:keys [instance field]} opts]
  `(~(symbol (str ".-" (name field))) ~(-emit-form* instance opts)))

(defmethod -emit-form :instance-call
  [{:keys [instance method args]} opts]
  `(~(symbol (str "." (name method))) ~(-emit-form* instance opts)
    ~@(mapv #(-emit-form* % opts) args)))

(defmethod -emit-form :prim-invoke
  [{:keys [fn args]} opts]
  `(~(-emit-form* fn opts)
    ~@(mapv #(-emit-form* % opts) args)))

(defmethod -emit-form :protocol-invoke
  [{:keys [protocol-fn target args]} opts]
  `(~(-emit-form* protocol-fn opts)
    ~(-emit-form* target opts)
    ~@(mapv #(-emit-form* % opts) args)))

(defmethod -emit-form :keyword-invoke
  [{:keys [target keyword]} opts]
  (list (-emit-form* keyword opts)
        (-emit-form* target opts)))

(defmethod -emit-form :instance?
  [{:keys [class target]} opts]
  `(instance? ~class ~(-emit-form* target opts)))

(defmethod -emit-form :var
  [{:keys [form ^clojure.lang.Var var]} opts]
  (if (or (:qualified-symbols opts)
          (:qualified-vars opts))
    (with-meta (symbol (-> var .ns ns-name name) (-> var .sym name))
      (meta form))
    form))

(defmethod -emit-form :def
  [ast opts]
  (let [f (default/-emit-form ast opts)]
    (if (:qualified-symbols opts)
      `(def ~(with-meta (symbol (-> ast :env :ns name) (str (second f)))
               (meta (second f)))
         ~@(nthrest f 2))
      f)))
