(ns render-fn
  (:require [nextjournal.clerk.sci-viewer :as v]))

(defn heading [x]
  (v/html [(keyword (str "h22" x)) (str "Hadin2g" x)]))
;;x
(defn paragraph [x]
  (v/html [:p x [:br] "~"]))
;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

nil
