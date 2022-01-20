;; # Viewers Meta
^{:nextjournal.clerk/visibility :hide-ns}
(ns ^:nextjournal.clerk/no-cache viewers-meta
  (:require [nextjournal.clerk :as clerk]))

;; Clerk's viewer api has been based on functions like in the following example.

(def tabular-data
  (clerk/table {:col-1 ["a" "b" "c"] :col-2 ["a" "b" "c"]}))

;; This isn't always what you want as it performs a tranformation of your data.
(keys tabular-data)

;; If this isn't what you want because you depend on it downstream, you can alternatively use metadata to convey the viewer.

^{::clerk/viewer clerk/table}
(def tabular-data-untouched
  {:col-1 ["a" "b" "c"] :col-2 ["a" "b" "c"]})

(keys tabular-data-untouched)

;; This also works on literals, not just on vars. Though you will less care about transformation in that case – as you'll not be holding a reference to it. As you see in the following example, you can also use keywords instead of functions. This is useful when you don't want to require Clerk.
^{::clerk/viewer :html}
[:h1 "Ohai Hiccup 👋"]
