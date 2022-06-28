(ns render-fn
  (:require [nextjournal.clerk.sci-viewer :as v]))

(defn heading [x]
  (v/html [(keyword (str "h" x)) (str "Heading ===========" x)]))

(defn paragraph [x]
  (v/html [:p x [:br] "~"]))
;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
