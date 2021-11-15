;; # Roll Dice 🎲
(ns ^:nextjournal.clerk/no-cache dice
  (:require [nextjournal.clerk :as clerk]))

(def dice '[🔴 🟢 🟡 🔵 🟠 🃏])

(clerk/with-viewer (fn [face]
                     (v/html [:div
                              [:h1 face]
                              [:button.button-primary {:on-click (fn [e]
                                                                   (v/clerk-eval '(clerk/show! "notebooks/dice.clj")))} "Roll!"]]))
  (rand-nth dice))

(defn shuffle []
  (clerk/show! "notebooks/dice.clj"))

#_(shuffle)
