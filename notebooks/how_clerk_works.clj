;; # How Clerk Works
;; Clerk uses static analysis and dataflow to avoid needless recomputation.
;; * we use `rewrite-clj` to parse the notebook file
;; * each form is analysed using `tools.analyzer`
(require '[nextjournal.clerk.hashing :as h]
         '[clojure.string :as str])

;; We also look at where a given symbol is coming from, this can be
;; * from Clojure source either form a jar or from the classpath
;; * from a java class in a jar
;; * built-in to the JDK
