;; # Plotly 📈
(ns viewers.plotly
  (:require [nextjournal.clerk.viewer :as v]))

;; ## Examples with Plotly's default options
;; See [Plotly's JavaScript docs](https://plotly.com/javascript/) for more examples and options

(v/plotly {:layout {:title "A surface plot"}
           :data [{:z [[1 2 3]
                       [3 2 1]]
                   :type "surface"}]})

(v/plotly {:layout {:title "A simple scatter plot with lines and markers"}
           :data [{:x [1 2 3 4]
                   :y [10 15 13 17]
                   :mode "markers"
                   :type "scatter"}
                  {:x [2 3 4 5]
                   :y [16 5 11 9]
                   :mode "lines"
                   :type "scatter"}
                  {:x [1 2 3 4]
                   :y [12 9 15 12]
                   :mode "lines+markers"
                   :type "scatter"}]})

;; ## Example with customized options

(v/plotly {:layout {:margin {:l 20 :r 0 :b 20 :t 0} :autosize false :width 300 :height 200}
           :data [{:x ["giraffes" "orangutans" "monkeys"]
                   :y [20 14 23]
                   :type "bar"}]})
