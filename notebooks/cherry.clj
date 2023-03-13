;; # Compile viewer functions using cherry
(ns notebooks.cherry
  #_{:nextjournal.clerk/visibility {:code :hide}
   :nextjournal.clerk/auto-expand-results? true}
  (:require [nextjournal.clerk :as clerk]))

(comment
  (clerk/clear-cache!)
  )

;; - [ ] TODO: compile :render-fn using cherry
;;   - [ ] TODO: vector is not defined: we need cherry function to live as global functions or prefix them using cherry?

(clerk/with-viewer
  {:render-fn
   '(fn [value]
      [:pre (time (do #_(dotimes [_ 100000]
                        (pr-str (interleave (cycle [1]) (frequencies [1 2 3 1 2 3]))))
                      (pr-str (interleave (cycle [1]) (frequencies [1 2 3 1 2 3])))))])}
  (+ 1 2 3 5))

(clerk/with-viewer
  {:render-fn
   (with-meta
     '(fn [value]
        [:pre
         (time (do #_(dotimes [_ 100000]
                       (pr-str (interleave (cycle [1]) (frequencies [1 2 3 1 2 3]))))
                   (pr-str (interleave (cycle [1]) (frequencies [1 2 3 1 2 3])))))])
     {::clerk/cherry true})}
  (+ 1 2 3 5))
