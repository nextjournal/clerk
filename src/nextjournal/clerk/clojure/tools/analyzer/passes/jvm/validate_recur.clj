;;   Copyright (c) Nicola Mometto, Rich Hickey & contributors.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;;   which can be found in the file epl-v10.html at the root of this distribution.
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.

(ns nextjournal.clerk.clojure.tools.analyzer.passes.jvm.validate-recur
  (:require [clojure.tools.analyzer.ast :refer [update-children]]
            [clojure.tools.analyzer.utils :refer [-source-info]]))

(defmulti validate-recur
  "Ensures recurs don't cross try boundaries"
  {:pass-info {:walk :pre :depends #{}}}
  :op)

(defmethod validate-recur :default [ast]
  (if (-> ast :env :no-recur)
    (update-children ast (fn [ast] (update-in ast [:env] assoc :no-recur true)))
    ast))

(defmethod validate-recur :try [ast]
  (update-children ast (fn [ast] (update-in ast [:env] assoc :no-recur true))))

(defmethod validate-recur :fn-method [ast]
  (update-in ast [:env] dissoc :no-recur))

(defmethod validate-recur :method [ast]
  (update-in ast [:env] dissoc :no-recur))

(defmethod validate-recur :loop [ast]
  (update-in ast [:env] dissoc :no-recur))

(defmethod validate-recur :recur [ast]
  (when (-> ast :env :no-recur)
    (throw (ex-info "Cannot recur across try"
                    (merge {:form (:form ast)}
                           (-source-info (:form ast) (:env ast))))))
  ast)
