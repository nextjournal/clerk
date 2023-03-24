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

;; Better performance:

(clerk/with-viewer
  {:render-fn
   '(fn [value]
      [:pre
       (time (do (dotimes [_ 100000]
                   (js/Math.sin 100))
                 (pr-str (interleave (cycle [1]) (frequencies [1 2 3 1 2 3])))))])
   :evaluator :cherry}
  (+ 1 2 3 5))

;; Let's use a render function in the :render-fn next

(clerk/with-viewer
  {:render-fn
   '(fn [value]
      [nextjournal.clerk.render/render-code "(+ 1 2 3)"])
   :evaluator :cherry}
  (+ 1 2 3 5))

;; Recursive ...

(clerk/with-viewer
  {:render-fn
   '(fn [value]
      [nextjournal.clerk.render/inspect {:a (range 30)}])
   :evaluator :cherry}
  nil)
