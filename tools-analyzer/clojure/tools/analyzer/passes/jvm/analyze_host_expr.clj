;;   Copyright (c) Nicola Mometto, Rich Hickey & contributors.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;;   which can be found in the file epl-v10.html at the root of this distribution.
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.

(ns nextjournal.clerk.clojure.tools.analyzer.passes.jvm.analyze-host-expr
  (:require [clojure.tools.analyzer :as ana]
            [clojure.tools.analyzer.utils :refer [ctx source-info merge']]
            [nextjournal.clerk.clojure.tools.analyzer.jvm.utils :refer :all]))

(defn maybe-static-field [[_ class sym]]
  (when-let [{:keys [flags type name]} (static-field class sym)]
    {:op          :static-field
     :assignable? (not (:final flags))
     :class       class
     :field       name
     :o-tag       type
     :tag         type}))

(defn maybe-static-method [[_ class sym]]
  (when-let [{:keys [name return-type]} (static-method class sym)]
    {:op      :static-call
     :tag     return-type
     :o-tag   return-type
     :class   class
     :method  name}))

(defn maybe-instance-method [target-expr class sym]
  (when-let [{:keys [return-type]} (instance-method class sym)]
    {:op       :instance-call
     :tag      return-type
     :o-tag    return-type
     :instance target-expr
     :class    class
     :method   sym
     :children [:instance]}))

(defn maybe-instance-field [target-expr class sym]
  (when-let [{:keys [flags name type]} (instance-field class sym)]
    {:op          :instance-field
     :assignable? (not (:final flags))
     :class       class
     :instance    target-expr
     :field       name
     :tag         type
     :o-tag       type
     :children    [:instance]}))

(defn analyze-host-call
  [target-type method args target-expr class env]
  (let [op (case target-type
             :static   :static-call
             :instance :instance-call)]
    (merge
     {:op     op
      :method method
      :args   args}
     (case target-type
       :static   {:class    class
                  :children [:args]}
       :instance {:instance target-expr
                  :class    (maybe-class (:tag target-expr))
                  :children [:instance :args]}))))

(defn analyze-host-field
  [target-type field target-expr class env]
  (if class
    (case target-type
      :static (or (maybe-static-field (list '. class field))
                  (throw (ex-info (str "Cannot find field "
                                       field " for class " class)
                                  (merge {:class class
                                          :field field}
                                         (source-info env)))))
      :instance (or (maybe-instance-field target-expr class field)
                    {:op          :host-interop
                     :target      (dissoc target-expr :tag :validated?)
                     :m-or-f      field
                     :assignable? true
                     :children    [:target]}
                    (when (:literal? target-expr)
                      (throw (ex-info (str "Cannot find field "
                                           field " for class " class)
                                      (merge {:instance (dissoc target-expr :env)
                                              :field    field}
                                             (source-info env)))))))
    {:op          :host-interop
     :target      target-expr
     :m-or-f      field
     :assignable? true
     :children    [:target]}))

(defn -analyze-host-expr
  [target-type m-or-f target-expr class env]
  (let [target-class (-> target-expr :tag)
        [field method] (if class
                         [(maybe-static-field (list '. class m-or-f))
                          (maybe-static-method (list '. class m-or-f))]
                         (when target-class
                           [(maybe-instance-field target-expr target-class m-or-f)
                            (maybe-instance-method target-expr target-class m-or-f)]))]
    (cond

     (not (or class target-class))
     {:op          :host-interop
      :target      target-expr
      :m-or-f      m-or-f
      :assignable? true
      :children    [:target]}

     method
     method

     field
     field

     class
     (throw (ex-info (str "Cannot find field or no-arg method call "
                          m-or-f " for class " class)
                     (merge {:class  class
                             :m-or-f m-or-f}
                            (source-info env))))

     target-class
     {:op          :host-interop
      :target      (dissoc target-expr :tag :validated?)
      :m-or-f      m-or-f
      :assignable? true
      :children    [:target]}

     :else
     (when (:literal? target-expr)
       (throw (ex-info (str "Cannot find field or no-arg method call "
                            m-or-f " for class " target-class)
                       (merge {:instance (dissoc target-expr :env)
                               :m-or-f   m-or-f}
                              (source-info env))))))))

(defn analyze-host-expr
  "Performing some reflection, transforms :host-interop/:host-call/:host-field
   nodes in either: :static-field, :static-call, :instance-call, :instance-field
   or :host-interop nodes, and a :var/:maybe-class/:maybe-host-form node in a
   :const :class node, if necessary (class literals shadow Vars).

   A :host-interop node represents either an instance-field or a no-arg instance-method. "
  {:pass-info {:walk :post :depends #{}}}
  [{:keys [op target form tag env class] :as ast}]
  (case op
    (:host-interop :host-call :host-field)
    (let [target (if-let [the-class (and (= :local (:op target))
                                         (maybe-class-literal (:form target)))]
                   (merge target
                          (assoc (ana/analyze-const the-class env :class)
                            :tag   Class
                            :o-tag Class))
                   target)
          class? (and (= :const (:op target))
                      (= :class (:type target))
                      (:form target))
          target-type (if class? :static :instance)]
      (merge' (dissoc ast :assignable? :target :args :children)
              (case op

                :host-call
                (analyze-host-call target-type (:method ast)
                                   (:args ast) target class? env)

                :host-field
                (analyze-host-field target-type (:field ast)
                                    target (or class? (:tag target)) env)

                :host-interop
                (-analyze-host-expr target-type (:m-or-f ast)
                                    target class? env))
              (when tag
                {:tag tag})))
    :var
    (if-let [the-class (and (not (namespace form))
                            (pos? (.indexOf (str form) "."))
                            (maybe-class-literal form))]
      (assoc (ana/analyze-const the-class env :class) :form form)
      ast)

    :maybe-class
    (if-let [the-class (maybe-class-literal class)]
      (assoc (ana/analyze-const the-class env :class) :form form)
      ast)

    :maybe-host-form
    (if-let [the-class (maybe-array-class-sym (symbol (str (:class ast))
                                                      (str (:field ast))))]
      (assoc (ana/analyze-const the-class env :class) :form form)
      ast)

    ast))
