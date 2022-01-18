;; # Viewers Meta
^{:nextjournal.clerk/visibility :hide-ns}
(ns viewers-meta
  (:require [nextjournal.clerk :as clerk]))

;; Clerk's viewer api has been based on functions like in the following example.
(def tabular-data
  (clerk/table {:col-1 ["a" "b" "c"] :col-2 ["a" "b" "c"]}))

;; This isn't always what you want as it performs a tranformation of your data.
(keys tabular-data)

;; If this isn't what you want because you depend on it downstream, you can alternatively use metadata to convey the viewer.
^{::clerk/viewer :html}
[:h1 "hi"]

^{::clerk/viewer clerk/html}
[:h1 "hi"]


^{::clerk/viewer clerk/table}
{:col-1 ["a" "b" "c"] :col-2 ["a" "b" "c"]}

^{::clerk/viewer :table}
{:col-1 ["a" "b" "c"] :col-2 ["a" "b" "c"]}

^{::clerk/viewer clerk/table}
(def tabular-data-untouched
  {:col-1 ["a" "b" "c"] :col-2 ["a" "b" "c"]})

(keys tabular-data-untouched)
