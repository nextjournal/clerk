;; # Rule 30
(ns rule-30
  (:require [nextjournal.viewer :as v]))

;; We start by defining custom viewers for `:number`, `:vector` and `:list`.
(def viewers
  {:number `(fn [x options]
              (v/html
               [:div.inline-block {:class (if (zero? x)
                                            "bg-white border-solid border-2 border-black"
                                            "bg-black")
                                   :style {:width 16 :height 16}}]))
   :vector `(fn [x options]
              (v/html (into [:div.flex.inline-flex] (map (partial v/inspect options)) x)))
   :list `(fn [x options]
            (v/html (into [:div.flex.flex-col] (map (partial v/inspect options)) x)))})

;; **TODO** `v/with-viewers` will be removed once Clerk supports global viewer registration.
(v/with-viewers 0 viewers)

(v/with-viewers 1 viewers)

(v/with-viewers [0 1 0] viewers)

(v/with-viewers '([0 1 0]
                  [1 0 1]) viewers)


(def rule
  (v/with-viewers
    {[1 1 1] 0
     [1 1 0] 0
     [1 0 1] 0
     [1 0 0] 1
     [0 1 1] 1
     [0 1 0] 1
     [0 0 1] 1
     [0 0 0] 0}
    viewers))

(let [n 33
      g1 (assoc (vec (repeat n 0)) (/ (dec n) 2) 1)
      evolve #(mapv rule (partition 3 1 (repeat 0) (cons 0 %)))]
  (v/with-viewers (->> g1 (iterate evolve) (take 17)) viewers))
