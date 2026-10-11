;; # Rule 30 🕹
;; Let's explore cellular automata in a Clerk Notebook. We start by requiring the custom viewers.
(ns rule-30
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.clerk.html :as html]))

(def viewers
  [{:pred number?
    :render-hiccup (fn [{:nextjournal/keys [value]}]
                     [:div.inline-block {:style "width:16px;height:16px"
                                         :class (if (pos? value) "bg-black" "bg-white border-solid border-2 border-black")}])}
   {:pred (every-pred list? (partial every? (some-fn number? vector?)))
    :render-hiccup (fn [{:nextjournal/keys [ctx value]}]
                     (into [:div.flex.flex-col] (html/inspect-children ctx value)))}
   {:pred (every-pred vector? (complement map-entry?) (partial every? number?))
    :render-hiccup (fn [{:nextjournal/keys [ctx value]}]
                     (into [:div.flex.inline-flex] (html/inspect-children ctx value)))}
   {:pred var? :transform-fn (clerk/update-val deref)}])

(clerk/add-viewers! viewers)

0

1

[0 1 0]

(list 0 1 0)

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

(def board
  (let [evolve #(mapv rule-30 (partition 3 1 (repeat 0) (cons 0 %)))]
    (->> first-generation (iterate evolve) (take 17) (apply list))))
