;; # Metadata-Based Viewer API
(ns viewers-meta
  (:require [nextjournal.clerk :as clerk]))

;; Clerk's viewer api has been based on functions like in the following example.

(def tabular-data
  (clerk/table {:col-1 ["a" "b" "c"] :col-2 ["a" "b" "c"]}))

;; This isn't always what you want as it performs a transformation of your data.
(keys tabular-data)

;; You can alternatively use metadata on the form to convey the viewer. This works with viewer functions.
^{::clerk/viewer clerk/table}
(def tabular-data-untouched
  {:col-1 ["a" "b" "c"] :col-2 ["a" "b" "c"]})

;; And see that it remains untouched. This comes with the added benefit that changing a viewer does not require a recomputation.
(keys tabular-data-untouched)

;; And it works with viewer vars. Also note that `nextjournal.clerk.viewer` isn't required, so this works without Clerk on the classpath. With the new `:as-alias` support in Clojure 1.11 we could also define an alias to not have to type the full ns.
^{::clerk/viewer nextjournal.clerk.viewer/table-viewer}
(def tabular-data-untouched-viewer-var
  {:col-1 ["a" "b" "c"] :col-2 ["a" "b" "c"]})
