;;   Copyright (c) Nicola Mometto, Rich Hickey & contributors.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;;   which can be found in the file epl-v10.html at the root of this distribution.
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.

(ns nextjournal.clerk.clojure.tools.analyzer.passes.jvm.annotate-host-info
  (:require [clojure.tools.analyzer :as ana]
            [clojure.tools.analyzer.ast :refer [prewalk]]
            [clojure.tools.analyzer.passes
             [cleanup :refer [cleanup]]
             [elide-meta :refer [elide-meta]]]
            [clojure.tools.analyzer.utils :refer [source-info]]
            [nextjournal.clerk.clojure.tools.analyzer.jvm.utils
             :refer [members name-matches? try-best-match]
             :as u]))

(defn annotate-host-info
  "Adds a :methods key to reify/deftype :methods info representing
   the reflected informations for the required methods, replaces
   (catch :default ..) forms with (catch Throwable ..)"
  {:pass-info {:walk :pre :depends #{} :after #{#'elide-meta}}}
  [{:keys [op methods interfaces class env] :as ast}]
  (case op
    (:reify :deftype)
    (let [all-methods
          (into #{}
                (mapcat (fn [class]
                          (mapv (fn [method]
                                  (dissoc method :exception-types))
                                (filter (fn [{:keys [flags return-type]}]
                                          (and return-type (not-any? #{:final :static} flags)))
                                        (members class))))
                        (conj interfaces Object)))]
      (assoc ast :methods (mapv (fn [ast]
                                  (let [name (:name ast)
                                        argc (count (:params ast))]
                                    (assoc ast :methods
                                           (filter #(and ((name-matches? name) (:name %))
                                                         (= argc (count (:parameter-types %))))
                                                   all-methods)))) methods)))


    :catch
    (let [the-class (cond

                     (and (= :const (:op class))
                          (= :default (:form class)))
                     Throwable

                     (= :maybe-class (:op class))
                     (u/maybe-class-literal (:class class)))

          ast (if the-class
                (-> ast
                  (assoc :class (assoc (ana/analyze-const the-class env :class)
                                  :form  (:form class)
                                  :tag   Class
                                  :o-tag Class)))
                ast)]
      (assoc-in ast [:local :tag]  (-> ast :class :val)))


    :method
    ;; this should actually be in validate but it's here since it needs to be prewalked
    ;; for infer-tag purposes
    (let [{:keys [name class tag form params fixed-arity env]} ast]
      (if interfaces
        (let [tags (mapv (comp u/maybe-class :tag meta :form) params)
              methods-set (set (mapv (fn [x] (dissoc x :declaring-class :flags)) methods))]
          (let [[m & rest :as matches] (try-best-match tags methods)]
            (if m
              (let [ret-tag  (u/maybe-class (:return-type m))
                    i-tag    (u/maybe-class (:declaring-class m))
                    arg-tags (mapv u/maybe-class (:parameter-types m))
                    params   (mapv (fn [{:keys [atom] :as arg} tag]
                                     (assoc arg :tag tag :o-tag tag)) params arg-tags)]
                (if (or (empty? rest)
                        (every? (fn [{:keys [return-type parameter-types]}]
                             (and (= (u/maybe-class return-type) ret-tag)
                                  (= arg-tags (mapv u/maybe-class parameter-types)))) rest))
                  (assoc (dissoc ast :interfaces :methods)
                    :bridges   (filter #(and (= arg-tags (mapv u/maybe-class (:parameter-types %)))
                                             (.isAssignableFrom (u/maybe-class (:return-type %)) ret-tag))
                                       (disj methods-set (dissoc m :declaring-class :flags)))
                    :methods   methods
                    :interface i-tag
                    :tag       ret-tag
                    :o-tag     ret-tag
                    :params    params)
                  (throw (ex-info (str "Ambiguous method signature for method: " name)
                                  (merge {:method     name
                                          :interfaces interfaces
                                          :form       form
                                          :params     (mapv (fn [x] (prewalk x cleanup)) params)
                                          :matches    matches}
                                         (source-info env))))))
              (throw (ex-info (str "No such method found: " name " with given signature in any of the"
                                   " provided interfaces: " interfaces)
                              (merge {:method     name
                                      :methods    methods
                                      :interfaces interfaces
                                      :form       form
                                      :params     params}
                                     (source-info env)))))))
        ast))
    ast))
