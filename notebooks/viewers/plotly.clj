;; # Plotly 📈
(ns viewers.plotly
  (:require [nextjournal.clerk.viewer :as v]))

(v/plotly {:data [{:z [[1 2 3]
                       [3 2 1]]
                   :type "surface"}]})
