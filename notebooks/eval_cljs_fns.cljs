(ns eval-cljs-fns
  (:require [nextjournal.clerk.sci-viewer :as v]))

(defn heading [x]
  (js/console.log "heading-fn")
  (v/html [(keyword (str "h" x)) (str "Heading45 yolo " x)]))
;;x
(defn paragraph [x]
  (v/html [:p x]))

[#'heading #'paragraph]

