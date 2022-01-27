;; # Nested Viewer Examples

^{:nextjournal.clerk/visibility #{:hide-ns}}
(ns ^:nextjournal.clerk/no-cache viewers-nested
  (:require [nextjournal.clerk :as clerk]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.walk :as w]))

;; ## 1. Support for viewers inside `html`
;; This examples uses hiccup to lay out a grid of graphs:

(let [layout {:yaxis {:visible false :fixedrange true}
              :xaxis {:visible false :fixedrange true}
              :width 280
              :height 200
              :showlegend false
              :margin {:l 0 :t 0 :b 0 :r 30}}]
  (clerk/html
    [:div.flex
     [:div.grid.grid-cols-2.gap-6.mx-auto
      [:figure.flex.flex-col.items-center.justify-end
       [:div.border
        (clerk/plotly
          {:data [{:x (range 10) :y (shuffle (range 10)) :mode "lines" :type "scatter"}
                  {:x (range 10) :y (shuffle (range 10)) :mode "lines" :type "scatter"}]
           :layout layout})]
       [:figcaption.text-sm.text-slate-500.sans-serif.mt-2.text-center
        "Line Plots"]]
      [:figure.flex.flex-col.items-center.justify-end
       [:div.border
        (clerk/plotly
          {:data [{:x (range 10) :y (shuffle (range 10)) :mode "markers" :type "scatter"}
                  {:x (range 10) :y (shuffle (range 10)) :mode "markers" :type "scatter"}]
           :layout layout})]
       [:figcaption.text-sm.text-slate-500.sans-serif.mt-2.text-center
        "Scatter Plots"]]
      [:figure.flex.flex-col.items-center.justify-end
       [:div.border
        (clerk/plotly
          {:data [{:x ["giraffes" "orangutans" "monkeys"] :y [20 14 23] :type "bar"}
                  {:x ["giraffes" "orangutans" "monkeys"] :y [12 18 29] :type "bar"}]
           :layout (assoc layout :barmode "group")})]
       [:figcaption.text-sm.text-slate-500.sans-serif.mt-2.text-center
        "Bar Charts"]]
      [:figure.flex.flex-col.items-center.justify-end
       [:div.border
        (clerk/plotly
          {:data [{:x (range 10) :y (shuffle (range 10)) :fill "tozeroy" :type "scatter"}
                  {:x (range 10) :y (shuffle (range 10)) :fill "tonexty" :type "scatter"}]
           :layout layout})]
       [:figcaption.text-sm.text-slate-500.sans-serif.mt-2.text-center
        "Filled Area Plots"]]]]))

;; ## 2. Support for viewers inside tables
;; This example shows sparkline graphs and formatted values in a table:

(defn sparkline [values]
  (clerk/vl {:data {:values (map-indexed (fn [i v] {:date i :price v}) values)}
             :mark {:type "line" :strokeWidth 1.2}
             :width 140
             :height 20
             :config {:background nil :border nil :view {:stroke "transparent"}}
             :encoding {:x {:field "date" :type "temporal" :axis nil :background nil}
                        :y {:field "price" :type "quantitative" :axis nil :background nil}}}))

(defn format-million [value]
  (clerk/html
    [:div.text-right.tabular-nums
     [:span.text-slate-400 "$M "] [:span (format "%,12d" value)]]))

(defn format-percent [value]
  (clerk/html
    [:div.text-right.tabular-nums
     (if (neg? value)
       [:span.text-red-500 "–"]
       [:span.text-green-500 "+"])
     [:span (format "%.2f" (max value (- value)))]
     [:span.text-slate-400 "%"]]))

(clerk/table
  {"" (repeatedly 5 #(sparkline (shuffle (range 50))))
   "Assets" (map format-million [64368 62510 50329 47355 40500])
   "Fund" ["Vanguard 500" "Fidelity Magellan" "Amer A Invest" "Amer A WA" "Pimco"]
   "4 wks" (map format-percent [-2 -2.1 -1.2 -1.5 -2.3])
   "2003" (map format-percent [12.2 11.3 9.4 9.4 2.4])
   "3 years" (map format-percent [-11.7 -12.9 -3.9 0.8 9.4])
   "5 years" (map format-percent [-0.8 -0.2 4.0 3.0 7.6])})

(clerk/show! "notebooks/tabledebug.clj")