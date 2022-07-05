(ns eval-cljs-fns
  (:require [nextjournal.clerk.sci-viewer :as v]))

(def answer 46)

(defn heading [text]
  (v/html [:h1 text]))
