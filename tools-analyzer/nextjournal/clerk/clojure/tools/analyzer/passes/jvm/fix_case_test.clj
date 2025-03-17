;;   Copyright (c) Nicola Mometto, Rich Hickey & contributors.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;;   which can be found in the file epl-v10.html at the root of this distribution.
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.

(ns nextjournal.clerk.clojure.tools.analyzer.passes.jvm.fix-case-test
  (:require [clojure.tools.analyzer.passes.add-binding-atom :refer [add-binding-atom]]))

(defn fix-case-test
  "If the node is a :case-test, annotates in the atom shared
   by the binding and the local node with :case-test"
  {:pass-info {:walk :pre :depends #{#'add-binding-atom}}}
  [ast]
  (when (:case-test ast)
    (swap! (:atom ast) assoc :case-test true))
  ast)
