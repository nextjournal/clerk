;;   Copyright (c) Nicola Mometto, Rich Hickey & contributors.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;;   which can be found in the file epl-v10.html at the root of this distribution.
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.

(ns nextjournal.clerk.clojure.tools.analyzer.passes.jvm.warn-on-reflection
  (:require [nextjournal.clerk.clojure.tools.analyzer.passes.jvm
             [validate-loop-locals :refer [validate-loop-locals]]
             [validate :refer [validate]]]))

(defn warn [what {:keys [file line column]}]
  (when *warn-on-reflection*
    (binding [*err* *out*]
      (println (str "Reflection warning: "
                    (when file
                      (str file ":"))
                    (when line
                      (str line ":"))
                    (when column
                      (str column " "))
                    "- " what)))))

(defmulti warn-on-reflection
  "Prints a warning to *err* when *warn-on-reflection* is true
   and a node requires runtime reflection"
  {:pass-info {:walk :pre :depends #{#'validate} :after #{#'validate-loop-locals}}}
  :op)

(defmethod warn-on-reflection :instance-call
  [ast]
  (when-not (:validated? ast)
    (warn (str "call to method " (:method ast) (when-let [class (:class ast)]
                                                 (str " on " (.getName ^Class class)))
               " cannot be resolved") (:env ast)))
  ast)

(defmethod warn-on-reflection :static-call
  [ast]
  (when-not (:validated? ast)
    (warn (str "call to static method " (:method ast) " on "
               (.getName ^Class (:class ast)) " cannot be resolved")
          (:env ast)))
  ast)

(defmethod warn-on-reflection :host-interop
  [ast]
  (warn (str "reference to field or no args method call " (:m-or-f ast)
             " cannot be resolved")
        (:env ast))
  ast)

(defmethod warn-on-reflection :new
  [ast]
  (when-not (:validated? ast)
    (warn (str "call to " (.getName ^Class (:val (:class ast))) " ctor cannot be resolved")
          (:env ast)))
  ast)

(defmethod warn-on-reflection :default [ast] ast)
