(ns eval-cljs-fns
  (:require [nextjournal.clerk.sci-viewer :as v]))

(defn heading [text]
  (v/html [:h3 text "!"]))

(defn paragraph [text]
  (v/html [:p {:style {:color "blue"}} text
           [:br]
           [:span {:style {:font-size "62px"}}
            "\u2767"]]))
