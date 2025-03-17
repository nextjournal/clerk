;;   Copyright (c) Nicola Mometto, Rich Hickey & contributors.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;;   which can be found in the file epl-v10.html at the root of this distribution.
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.

(ns nextjournal.clerk.clojure.tools.analyzer.passes.jvm.annotate-branch)

(defmulti annotate-branch
  "Adds :branch? to branch AST nodes (if/case), :test? to the test children
   node of the branching op and :path? to the branching paths.

   Example: {:op if :branch? true :test {:test? true ..} :then {:path? true ..} ..}"
  {:pass-info {:walk :any :depends #{}}}
  :op)

(defmethod annotate-branch :if
  [ast]
  (-> ast
    (assoc :branch? true)
    (assoc-in [:test :test?] true)
    (assoc-in [:then :path?] true)
    (assoc-in [:else :path?] true)))

(defmethod annotate-branch :fn-method
  [ast]
  (assoc ast :path? true))

(defmethod annotate-branch :method
  [ast]
  (assoc ast :path? true))

(defmethod annotate-branch :case
  [ast]
  (-> ast
    (assoc :branch? true)
    (assoc-in [:test :test?] true)
    (assoc-in [:default :path?] true)))

(defmethod annotate-branch :case-then
  [ast]
  (assoc ast :path? true))

(defmethod annotate-branch :default [ast] ast)
