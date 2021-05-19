;; # How Clerk Works 🧐
(ns how-clerk-works
  (:require [nextjournal.clerk.hashing :as h]
            [weavejester.dependency :as dep]))
;; ## Step 1: Parsing
;; First, we parse a given Clojure file using `rewrite-clj`.
(def parsed
  (h/parse-file {:markdown? true} "notebooks/how_clerk_works.clj"))

;; ## Step 2: Analysis
;; Then, each expression is analysed using `tools.analyzer`. A dependency graph, the analyzed form and the originating file is recorded.

(def analyzed
  (-> (h/analyze-file "notebooks/how_clerk_works.clj")
      (get-in [:var->hash #'analyzed])))


;; ### Including Transative Depedencies
;; This analysis is done recurively decents into all dependency symbols.

(h/find-location #'inc)

(h/find-location #'dep/depend)

(h/find-location  io.methvin.watcher.DirectoryChangeEvent)

(h/find-location java.util.UUID)


;; ## Step 3: Hashing
;; Then we can use this information to hash each expression.

(def hashed

  (h/hash "notebooks/how_clerk_works.clj"))
