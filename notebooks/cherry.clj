;; # Compile viewer functions using cherry
(ns notebooks.cherry
  #_{:nextjournal.clerk/visibility {:code :hide}
   :nextjournal.clerk/auto-expand-results? true}
  (:require [nextjournal.clerk :as clerk]))

;; - [ ] TODO: compile :render-fn using cherry
;;   - [ ] TODO: vector is not defined: we need cherry function to live as global functions or prefix them using cherry?

(clerk/with-viewer {:render-fn '(fn [value]
                                  [:pre (pr-str (frequencies [1 2 3 1 2 3]))])}
  (+ 1 2 3 5))
