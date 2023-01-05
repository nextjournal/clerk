;; # 🔠 Grid Layouts
^{:nextjournal.clerk/visibility {:code :hide}}
(ns grid
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as v]))

^{::clerk/visibility {:code :hide :result :hide}}
(def image-1 (clerk/image "https://nextjournal.com/data/QmU9dbBd89MUK631CoCtTwBi5fX4Hgx2tTPpiL4VStg8J7?filename=a.gif&content-type=image/gif"))

^{::clerk/visibility {:code :hide :result :hide}}
(def image-2 (clerk/image "https://nextjournal.com/data/QmfKZzHCBQKU7KKXQqcje5cgR6zLge3CcxeuZe8moUkJxf?filename=b.gif&content-type=image/gif"))

^{::clerk/visibility {:code :hide :result :hide}}
(def image-3 (clerk/image "https://nextjournal.com/data/QmXALbNeDD6NSudgVfHE5SvY1Xjzbj7TSWnARqcZrvXsss?filename=c.gif&content-type=image/gif"))

;; ## Layouts can be composed via `row`s and `col`s
;;
;; Passing `:width`, `:height` or any other style attributes to `::clerk/opts`
;; will assign them on the row or col that contains your items. You can use
;; this to size your containers accordingly.

(v/row image-1 image-2 image-3)

(v/col {::clerk/opts {:width 150}} image-1 image-2 image-3)

;; Laying out stuff is not limited to images. You can use it to lay out any Clerk viewer. E.g. combine it
;; with HTML viewers:

(v/row
 (v/html [:svg {:width 100 :height 100} [:circle {:r 50 :cx 50 :cy 50 :fill "red"}]])
 (v/html [:svg {:width 100 :height 100} [:circle {:r 50 :cx 50 :cy 50 :fill "green"}]])
 (v/html [:svg {:width 100 :height 100} [:circle {:r 50 :cx 50 :cy 50 :fill "blue"}]]))

;; Or use it with Plotly or Vega Lite viewers to lay out a simple dashboard:

^{::clerk/visibility {:code :hide :result :hide}}
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

^{::clerk/visibility {:code :hide :result :hide}}
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
