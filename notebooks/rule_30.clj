;; # Rule 30 🕹
;; Let's explore cellular automata in a Clerk Notebook. We start by requiring the custom viewers.
(ns rule-30
  (:require [nextjournal.viewer :as v]))

;; Now let's define Rule 30 as a map. It maps a vector of three cells to a new value for a cell. Notice how the map viewer can be used as-is and uses our number and vector viewers.
(def rule-30
  {[1 1 1] 0
   [1 1 0] 0
   [1 0 1] 0
   [1 0 0] 1
   [0 1 1] 1
   [0 1 0] 1
   [0 0 1] 1
   [0 0 0] 0})

;; Our first generation is a row with 33 elements. The element at the center is a black square, all other squares are white.
(def first-generation
  (let [n 33]
    (assoc (vec (repeat n 0)) (/ (dec n) 2) 1)))

;; Finally, we can evolve the board.
(let [evolve #(mapv rule-30 (partition 3 1 (repeat 0) (cons 0 %)))]
  (->> first-generation (iterate evolve) (take 17)))


(v/register-viewers!
 {:number #(v/html [:div.inline-block {:class (if (zero? %)
                                                "bg-white border-solid border-2 border-black"
                                                "bg-black")
                                       :style {:width 16 :height 16}}])
  :vector (fn [x options]
            (v/html (into [:div.flex.inline-flex] (map (partial v/inspect options)) x)))
  :list (fn [x options]
          (v/html (into [:div.flex.flex-col] (map (partial v/inspect options)) x)))})
