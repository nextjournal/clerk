;;   Copyright (c) Nicola Mometto, Rich Hickey & contributors.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;;   which can be found in the file epl-v10.html at the root of this distribution.
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.

(ns nextjournal.clerk.clojure.tools.analyzer.passes.jvm.infer-tag
  (:require [clojure.tools.analyzer.utils :refer [arglist-for-arity]]
            [nextjournal.clerk.clojure.tools.analyzer.jvm.utils :as u]
            [clojure.tools.analyzer.env :as env]
            [clojure.set :refer [rename-keys]]
            [clojure.tools.analyzer.passes.trim :refer [trim]]
            [nextjournal.clerk.clojure.tools.analyzer.passes.jvm
             [annotate-tag :refer [annotate-tag]]
             [annotate-host-info :refer [annotate-host-info]]
             [analyze-host-expr :refer [analyze-host-expr]]
             [fix-case-test :refer [fix-case-test]]]))

(defmulti -infer-tag :op)
(defmethod -infer-tag :default [ast] ast)

(defmethod -infer-tag :binding
  [{:keys [init atom] :as ast}]
  (if init
    (let [info (select-keys init [:return-tag :arglists])]
      (swap! atom merge info)
      (merge ast info))
    ast))

(defmethod -infer-tag :local
  [ast]
  (let [atom @(:atom ast)]
    (merge atom
           ast
           {:o-tag (:tag atom)})))

(defmethod -infer-tag :var
  [{:keys [var form] :as ast}]
  (let [{:keys [tag arglists]} (:meta ast)
        arglists (if (= 'quote (first arglists))
                   (second arglists)
                   arglists)
        form-tag (:tag (meta form))]
    ;;if (not dynamic)
    (merge ast
           {:o-tag Object}
           (when-let [tag (or form-tag tag)]
             (if (fn? @var)
               {:tag clojure.lang.AFunction :return-tag tag}
               {:tag tag}))
           (when arglists
             {:arglists arglists}))))

(defmethod -infer-tag :def
  [{:keys [var init name] :as ast}]
  (let [info (merge (select-keys init [:return-tag :arglists :tag])
                    (select-keys (meta name) [:tag :arglists]))]
    (when (and (seq info)
               (not (:dynamic (meta name)))
               (= :global (-> (env/deref-env) :passes-opts :infer-tag/level)))
      (alter-meta! var merge (rename-keys info {:return-tag :tag})))
    (merge ast info {:tag clojure.lang.Var :o-tag clojure.lang.Var})))

(defmethod -infer-tag :quote
  [ast]
  (let [tag (-> ast :expr :tag)]
    (assoc ast :tag tag :o-tag tag)))

(defmethod -infer-tag :new
  [ast]
  (let [t (-> ast :class :val)]
    (assoc ast :o-tag t :tag t)))

(defmethod -infer-tag :with-meta
  [{:keys [expr] :as ast}]
  (merge ast (select-keys expr [:return-tag :arglists])
         {:tag (or (:tag expr) Object) :o-tag Object})) ;;trying to be smart here

(defmethod -infer-tag :recur
  [ast]
  (assoc ast :ignore-tag true))

(defmethod -infer-tag :do
  [{:keys [ret] :as ast}]
  (merge ast (select-keys ret [:return-tag :arglists :ignore-tag :tag])
         {:o-tag (:tag ret)}))

(defmethod -infer-tag :let
  [{:keys [body] :as ast}]
  (merge ast (select-keys body [:return-tag :arglists :ignore-tag :tag])
         {:o-tag (:tag body)}))

(defmethod -infer-tag :letfn
  [{:keys [body] :as ast}]
  (merge ast (select-keys body [:return-tag :arglists :ignore-tag :tag])
         {:o-tag (:tag body)}))

(defmethod -infer-tag :loop
  [{:keys [body] :as ast}]
  (merge ast (select-keys body [:return-tag :arglists])
         {:o-tag (:tag body)}
         (let [tag (:tag body)]
           (if (#{Void Void/TYPE} tag)
             (assoc ast :tag Object)
             (assoc ast :tag tag)))))

(defn =-arglists? [a1 a2]
  (let [tag (fn [x] (-> x meta :tag u/maybe-class))]
    (and (= a1 a2)
         (every? true? (mapv (fn [a1 a2]
                       (and (= (tag a1) (tag a2))
                            (= (mapv tag a1)
                               (mapv tag a2))))
                     a1 a2)))))

(defmethod -infer-tag :if
  [{:keys [then else] :as ast}]
  (let [then-tag (:tag then)
        else-tag (:tag else)
        ignore-then? (:ignore-tag then)
        ignore-else? (:ignore-tag else)]
    (cond
     (and then-tag
          (or ignore-else? (= then-tag else-tag)))
     (merge ast
            {:tag then-tag :o-tag then-tag}
            (when-let [return-tag (:return-tag then)]
              (when (or ignore-else?
                        (= return-tag (:return-tag else)))
                {:return-tag return-tag}))
            (when-let [arglists (:arglists then)]
              (when (or ignore-else?
                        (=-arglists? arglists (:arglists else)))
                {:arglists arglists})))

     (and else-tag ignore-then?)
     (merge ast
            {:tag else-tag :o-tag else-tag}
            (when-let [return-tag (:return-tag else)]
              {:return-tag return-tag})
            (when-let [arglists (:arglists else)]
              {:arglists arglists}))

     (and (:ignore-tag then) (:ignore-tag else))
     (assoc ast :ignore-tag true)

     :else
     ast)))

(defmethod -infer-tag :throw
  [ast]
  (assoc ast :ignore-tag true))

(defmethod -infer-tag :case
  [{:keys [thens default] :as ast}]
  (let [thens (conj (mapv :then thens) default)
        exprs (seq (remove :ignore-tag thens))
        tag (:tag (first exprs))]
    (cond
     (and tag
          (every? #(= (:tag %) tag) exprs))
     (merge ast
            {:tag tag :o-tag tag}
            (when-let [return-tag (:return-tag (first exprs))]
              (when (every? #(= (:return-tag %) return-tag) exprs)
                {:return-tag return-tag}))
            (when-let [arglists (:arglists (first exprs))]
              (when (every? #(=-arglists? (:arglists %) arglists) exprs)
                {:arglists arglists})))

     (every? :ignore-tag thens)
     (assoc ast :ignore-tag true)

     :else
     ast)))

(defmethod -infer-tag :try
  [{:keys [body catches] :as ast}]
  (let [{:keys [tag return-tag arglists]} body
        catches (remove :ignore-tag (mapv :body catches))]
    (merge ast
           (when (and tag (every? #(= % tag) (mapv :tag catches)))
             {:tag tag :o-tag tag})
           (when (and return-tag (every? #(= % return-tag) (mapv :return-tag catches)))
             {:return-tag return-tag})
           (when (and arglists (every? #(=-arglists? % arglists) (mapv :arglists catches)))
             {:arglists arglists}))))

(defmethod -infer-tag :fn-method
  [{:keys [form body params local] :as ast}]
  (let [annotated-tag (or (:tag (meta (first form)))
                          (:tag (meta (:form local))))
        body-tag (:tag body)
        tag (or annotated-tag body-tag)
        tag (if (#{Void Void/TYPE} tag)
              Object
              tag)]
    (merge (if (not= tag body-tag)
             (assoc-in ast [:body :tag] (u/maybe-class tag))
             ast)
           (when tag
             {:tag   tag
              :o-tag tag})
           {:arglist (with-meta (vec (mapcat (fn [{:keys [form variadic?]}]
                                               (if variadic? ['& form] [form]))
                                             params))
                       (when tag {:tag tag}))})))

(defmethod -infer-tag :fn
  [{:keys [local methods] :as ast}]
  (merge ast
         {:arglists (seq (mapv :arglist methods))
          :tag      clojure.lang.AFunction
          :o-tag    clojure.lang.AFunction}
         (when-let [tag (or (:tag (meta (:form local)))
                            (and (apply = (mapv :tag methods))
                                 (:tag (first methods))))]
           {:return-tag tag})))

(defmethod -infer-tag :invoke
  [{:keys [fn args] :as ast}]
  (if (:arglists fn)
    (let [argc (count args)
          arglist (arglist-for-arity fn argc)
          tag (or (:tag (meta arglist))
                  (:return-tag fn)
                  (and (= :var (:op fn))
                       (:tag (:meta fn))))]
      (merge ast
             (when tag
               {:tag     tag
                :o-tag   tag})))
    (if-let [tag (:return-tag fn)]
      (assoc ast :tag tag :o-tag tag)
      ast)))

(defmethod -infer-tag :method
  [{:keys [form body params] :as ast}]
  (let [tag (or (:tag (meta (first form)))
                (:tag (meta (second form))))
        body-tag (:tag body)]
    (assoc ast :tag (or tag body-tag) :o-tag body-tag)))

(defmethod -infer-tag :reify
  [{:keys [class-name] :as ast}]
  (assoc ast :tag class-name :o-tag class-name))

(defmethod -infer-tag :set!
  [ast]
  (let [t (:tag (:target ast))]
    (assoc ast :tag t :o-tag t)))

(defn infer-tag
  "Performs local type inference on the AST adds, when possible,
   one or more of the following keys to the AST:
   * :o-tag      represents the current type of the
                 expression represented by the node
   * :tag        represents the type the expression represented by the
                 node is required to have, possibly the same as :o-tag
   * :return-tag implies that the node will return a function whose
                 invocation will result in a object of this type
   * :arglists   implies that the node will return a function with
                 this arglists
   * :ignore-tag true when the node is untyped, does not imply that
                 all untyped node will have this

  Passes opts:
  * :infer-tag/level  If :global, infer-tag will perform Var tag
                      inference"
  {:pass-info {:walk :post :depends #{#'annotate-tag #'annotate-host-info #'fix-case-test #'analyze-host-expr} :after #{#'trim}}}
  [{:keys [tag form] :as ast}]
  (let [tag (or tag (:tag (meta form)))
        ast (-infer-tag ast)]
    (merge ast
           (when tag
             {:tag tag})
           (when-let [o-tag (:o-tag ast)]
             {:o-tag o-tag}))))
