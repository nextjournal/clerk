;; # Compile viewer functions using cherry
(ns notebooks.cherry
  #_{:nextjournal.clerk/visibility {:code :hide}
   :nextjournal.clerk/auto-expand-results? true}
  (:require [nextjournal.clerk :as clerk]))

(comment
  (clerk/clear-cache!)
  )

(clerk/with-viewer
  {:render-fn
   '(fn [value]
      [:pre (time (do (dotimes [_ 100000]
                        (js/Math.sin 100))
                      (pr-str (interleave (cycle [1]) (frequencies [1 2 3 1 2 3])))))])}
  (+ 1 2 3 5))

(clerk/with-viewer
  {:render-fn
   '(fn [value]
      [:pre
       (time (do (dotimes [_ 100000]
                   (js/Math.sin 100))
                 (pr-str (interleave (cycle [1]) (frequencies [1 2 3 1 2 3])))))])
   :cherry true}
  (+ 1 2 3 5))
