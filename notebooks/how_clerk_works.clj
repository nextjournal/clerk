;; # How Clerk Works
;; Clerk uses static analysis and dataflow to avoid needless recomputation.
;; * we use `rewrite-clj` to parse the notebook file
;; * each form is analysed using `tools.analyzer`
(ns how-clerk-works
  (:require [nextjournal.clerk.hashing :as h]
            [weavejester.dependency :as dep]))


;; We also look at where a given symbol is coming from, this can be
;; * from Clojure source either form a jar or from the classpath
;; * from a java class in a jar
;; * built-in to the JDK
(into {}
      (map (juxt #(if (var? %) (symbol %) %) h/find-location))
      [#'h/find-location #'inc #'dep/depend io.methvin.watcher.DirectoryChangeEvent java.util.UUID])
