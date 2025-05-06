;; # Conditional Read
(ns conditional-read)

;; We do read the `:clj` branch.

#?(:clj (+ 39 3))

;; The following code block is removed since it doesn't contain a
;; `:clj` branch.

#?(:cljs (inc 41))

