;; # Conditional Read
(ns conditional-read
  (:require [demo.lib :as d]))

(d/fix-case-3 "foo")

;; Clerk gives you a rich notebook experience, built on top of *regular* Clojure namespaces, enhanced with markdown comments.
#?(:clj (+ 39 3))

;; bar baz
