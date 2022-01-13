;; # Nanu Würfel 🎲
(ns dice
  (:require [clojure.java.shell :as shell]
            [nextjournal.clerk :as clerk]))

;; My kids have [this game](https://www.ravensburger.de/produkte/spiele/mitbringspiele/nanu-23063/index.html) and we lost the dice that comes with it. It can't be too hard to make on in Clojure, can it? The dice has five colored `sides` and a joker.
(def sides '[🟡 🟠 🟢 🔵 🃏])

;; Next, we'll use an `atom` that will hold the state.
(defonce dice (atom nil))

;; Here, we define a viewer using hiccup that will the dice as well as a button. Note that this button has an `:on-click` event handler that uses `v/clerk-eval` to tell Clerk to evaluate the argument, in this cases `(roll!)` when clicked.
^::clerk/no-cache
(clerk/with-viewer '(fn [side]
                      (v/html [:div.text-center
                               (when side
                                 [:div.mt-2 {:style {:font-size "6em"}} side])
                               [:button.bg-blue-500.hover:bg-blue-700.text-white.font-bold.py-2.px-4.rounded
                                {:on-click (fn [e] (v/clerk-eval '(roll!)))} "Roll 🎲!"]]))
  @dice)

;; Our roll! function `resets!` our `dice` with a random side and prints and says the result. Finally it updates the notebook.
(defn roll! []
  (reset! dice (rand-nth sides))
  (prn @dice)
  (shell/sh "say" (name ('{🟡 :gelb 🟠 :orange 🟢 :grün 🔵 :blau 🃏 :joker} @dice)))
  (clerk/show! "notebooks/dice.clj"))


#_(roll!)
