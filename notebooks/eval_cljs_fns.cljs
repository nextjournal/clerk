(ns eval-cljs-fns
  (:require [nextjournal.clerk.sci-viewer :as v]))

(defn heading [text]
  (v/html [:h3 text "!!"]))
