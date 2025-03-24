(ns sci-async
  (:require ["https://cdn.skypack.dev/canvas-confetti" :as confetti]))

(defn my-viewer [_]
  [:div {:on-click nil #_#(confetti.default)} "Hello"])

(prn ::done)
