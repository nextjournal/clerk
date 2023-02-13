;; # Test Cases for Auto-Expanding Data Structure Viewer
(ns notebooks.cherry
  #_{:nextjournal.clerk/visibility {:code :hide}
   :nextjournal.clerk/auto-expand-results? true}
  (:require [nextjournal.clerk :as clerk]))

;; - [ ] TODO: compile :render-fn using cherry

(clerk/with-viewer {:render-fn '(fn [value]
                                  [:pre value])}
  (+ 1 2 3 5))
