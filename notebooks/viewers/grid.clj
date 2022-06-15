;; # 🔠 Grid Layouts
(ns grid
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as v])
  (:import (java.net URL)
           (javax.imageio ImageIO)))

^{::clerk/visibility :hide ::clerk/viewer v/hide-result}
(def image-1 (ImageIO/read (URL. "https://etc.usf.edu/clipart/62300/62370/62370_letter-a_lg.gif")))

^{::clerk/visibility :hide ::clerk/viewer v/hide-result}
(def image-2 (ImageIO/read (URL. "https://etc.usf.edu/clipart/72700/72783/72783_floral_b_lg.gif")))

^{::clerk/visibility :hide ::clerk/viewer v/hide-result}
(def image-3 (ImageIO/read (URL. "https://etc.usf.edu/clipart/72700/72787/72787_floral_c_lg.gif")))

;; ## Layouts can be composed via `row`s and `col`s
;;
;; Passing `:width`, `:height` or any other style attributes to `::clerk/opts`
;; will assign them on the row or col that contains your items. You can use
;; this to size your containers accordingly.

(v/row image-1 image-2 image-3)

(v/col {::clerk/opts {:width 150}} image-1 image-2 image-3)

;; Laying out stuff is not limited to images. You can use it to lay out any Clerk viewer. E.g. combine it
;; with HTML viewers to render nice captions:

(defn caption [text]
  (v/html [:span.text-slate-500.text-xs.text-center.font-sans text]))

(v/row
  (v/col image-1 (caption "Figure 1: Decorative A"))
  (v/col image-2 (caption "Figure 2: Decorative B"))
  (v/col image-3 (caption "Figure 3: Decorative C")))

;; Or use it with Plotly or Vega Lite viewers to lay out a simple dashboard:

^{::clerk/visibility :hide ::clerk/viewer v/hide-result}
(def donut-chart
  (v/plotly {:data [{:values [27 11 25 8 1 3 25]
                     :labels ["US" "China" "European Union" "Russian Federation" "Brazil" "India" "Rest of World"]
                     :text "CO2"
                     :textposition "inside"
                     :domain {:column 1}
                     :hoverinfo "label+percent+name"
                     :hole 0.4
                     :type "pie"}]
             :layout {:showlegend false
                      :width 200
                      :height 200
                      :annotations [{:font {:size 20} :showarrow false :x 0.5 :y 0.5 :text "CO2"}]}
             :config {:responsive true}}))

^{::clerk/visibility :hide ::clerk/viewer v/hide-result}
(def contour-plot
  (v/plotly {:data [{:z [[10 10.625 12.5 15.625 20]
                         [5.625 6.25 8.125 11.25 15.625]
                         [2.5 3.125 5.0 8.125 12.5]
                         [0.625 1.25 3.125 6.25 10.625]
                         [0 0.625 2.5 5.625 10]]
                     :type "contour"}]}))

(v/col
  (v/row donut-chart donut-chart donut-chart)
  contour-plot)

;; ## Alternative notations
;;
;; By default, `row` and `col` operate on `& rest` so you can pass any number of items to the functions.
;; But the viewers are smart enough to accept any sequential list of items so you can
;; assign the viewers via metadata on your data structures too.

^{::clerk/viewer :row}
[image-1 image-2 image-3]

^{::clerk/viewer v/row}
[image-1 image-2 image-3]

(v/row [image-1 image-2 image-3])

^{::clerk/viewer v/col ::clerk/opts {:width 150}}
[image-1 image-2 image-3]