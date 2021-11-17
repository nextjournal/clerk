;; # Roll Dice 🎲
(ns ^:nextjournal.clerk/no-cache dice
  (:require [nextjournal.clerk :as clerk]))

(def dice '[⚀ ⚁ ⚂ ⚃ ⚄ ⚅])

(clerk/with-viewer #(v/html [:div.text-center
                             [:div.mt-2 {:style {:font-size "6em"}} %]
                             [:button.bg-blue-500.hover:bg-blue-700.text-white.font-bold.py-2.px-4.rounded
                              {:on-click (fn [e] (v/clerk-eval '(roll!)))} "Roll 🎲!"]])
  (rand-nth dice))

(defn roll! []
  (clerk/show! "notebooks/dice.clj"))

#_(roll!)
