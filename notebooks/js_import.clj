;; # 📦 Dynamic JS Imports
(ns js-import
  (:require [clojure.data.csv :as csv]
            [nextjournal.clerk :as clerk]
            [nextjournal.clerk]
            [nextjournal.clerk.viewer :as viewer]))

;; This example uses [Observable Plots](https://observablehq.com/plot) with data from https://allisonhorst.github.io/palmerpenguins/
(defn parse-float [^String s] (Float/parseFloat s))

(def observable-plot-viewer
  {:transform-fn clerk/mark-presented
   :render-fn
   '(fn [data _]
      [nextjournal.clerk.render/with-dynamic-import
       {:module "https://cdn.skypack.dev/@observablehq/plot@0.5"}
       (fn [Plot]
         (let [dot-plot (.. Plot
                            (dot (clj->js data)
                                 (j/obj :x "flipper_length_mm"
                                        :y "body_mass_g"
                                        :fill "species"))
                            (plot (j/obj :grid true)))
               legend (.legend dot-plot "color")]
           [:div {:ref (fn [el]
                         (if el
                           (doto el
                             (.append legend)
                             (.append dot-plot))
                           (do (.remove legend)
                               (.remove dot-plot))))}]))])})

^{::clerk/viewer observable-plot-viewer}
(def palmer-penguins
  (-> (slurp "https://nextjournal.com/data/Qmf6FJyJxBQnB6TUZ3J9pdzHSs8UoewoY6WfdZHu1XxkD8?filename=penguins.csv&content-type=text/csv")
      (csv/read-csv)
      viewer/use-headers
      (as-> data
          (let [{:keys [head rows]} data]
            (map (fn [row] (zipmap head (reduce #(update %1 %2 parse-float) row [1 2 3 4])))
                 rows)))))

;; or use `js/import` directly:
(nextjournal.clerk/with-viewer
  '(fn [_]
     (let [confetti (nextjournal.clerk.render.hooks/use-promise (js/import "https://cdn.skypack.dev/canvas-confetti"))]
       [:button.bg-teal-500.hover:bg-teal-700.text-white.font-bold.py-2.px-4.rounded.rounded-full.font-sans
        (if confetti {:on-click #(.default confetti)} {:class "bg-gray-200"}) "Peng 🎉!"])) {})
