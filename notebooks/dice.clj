;; # Roll Dice 🎲
(ns ^:nextjournal.clerk/no-cache dice
  (:require [nextjournal.clerk :as clerk]))

(def dice '[🔴 🟢 🟡 🔵 🟠 🃏])

(clerk/with-viewer (fn [face]
                     (v/html [:div.text-center
                              [:div.mt-2 {:style {:font-size "6em"}} face]
                              [:button.bg-blue-500.hover:bg-blue-700.text-white.font-bold.py-2.px-4.rounded
                               {:on-click (fn [e] (v/clerk-eval '(shuffle)))} "Roll 🎲!"]]))
  (rand-nth dice))

(defn shuffle []
  (clerk/show! "notebooks/dice.clj"))

#_(shuffle)
