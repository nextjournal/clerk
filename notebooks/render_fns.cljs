(ns render-fns
  "An illustration on how to write Clerk render functions in a cljs file."
  (:require [nextjournal.clerk.viewer :as v]))

(defn heading [text]
  (v/html [:h3 text "!"]))

(defn paragraph
  [text]
  (v/html [:p {:style {:color "darkblue"}} text
           [:br]
           [:span {:style {:font-size "62px"}}
            "\u2767"]]))
