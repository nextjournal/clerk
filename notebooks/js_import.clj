;; # 📦 Dynamic JS Imports
(ns js-import
  {:nextjournal.clerk/visibility {:code :hide :result :hide}}
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as clerk.viewer]
            [clojure.data.csv :as csv]))

;; This example uses [Observable Plots](https://observablehq.com/plot) with data from https://allisonhorst.github.io/palmerpenguins/

(defn parse-float [^String s] (Float/parseFloat s))

^{::clerk/visibility {:code :show}}
(def palmer-penguins
  (-> (slurp "https://nextjournal.com/data/Qmf6FJyJxBQnB6TUZ3J9pdzHSs8UoewoY6WfdZHu1XxkD8?filename=penguins.csv&content-type=text/csv")
      (csv/read-csv)
      clerk.viewer/use-headers
      clerk.viewer/normalize-table-data
      (as-> data
        (let [{:keys [head rows]} data]
          (map (fn [row] (zipmap head (reduce #(update %1 %2 parse-float) row [1 2 3 4])))
               rows)))))

(clerk/table palmer-penguins)

^{::clerk/visibility {:code :show}}
(def observable-plot-viewer
  {:transform-fn clerk/mark-presented
   :render-fn
   '(fn [data _]
      [nextjournal.clerk.render/with-dynamic-import
       {:module "https://cdn.skypack.dev/@observablehq/plot@0.6"}
       (fn [Plot]
         [:div {:ref (fn [el]
                       (when el
                         (.append el
                                  (.. Plot
                                      (dot (clj->js data) (j/obj :x "flipper_length_mm" :y "body_mass_g" :fill "species"))
                                      (plot (j/obj :grid true))))))}])])})

^{::clerk/viewer observable-plot-viewer
  ::clerk/visibility {:result :show}}
palmer-penguins

;; or use `js/import` directly:
^{::clerk/visibility {:result :show :code :show} ::clerk/no-cache true}
(nextjournal.clerk/with-viewer
  '(fn [n]
     (let [cc (nextjournal.clerk.render.hooks/use-promise
               (js/import "https://cdn.skypack.dev/canvas-confetti"))]
       (nextjournal.clerk.render.hooks/use-effect #(when cc (.default cc)) [n])
       [:<>])) (rand))
