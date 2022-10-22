(ns render-fns
  "An illustration on how to write Clerk render functions in a cljs file."
  (:require [nextjournal.clerk.viewer :as v]))

(defn heading [text]
  [:h3 text "!"])

(defn paragraph
  [text]
  [:p {:style {:color "darkblue"}} text
   [:br]
   [:span {:style {:font-size "62px"}}
    "\u2767"]])
