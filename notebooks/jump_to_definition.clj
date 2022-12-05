;; # 🤾🏼 Jump to Definition
(ns jump-to-definition
  (:require
   [clojure.string :as str]
   [nextjournal.clerk :as clerk]
   [nextjournal.clerk.render :as-alias render]
   [nextjournal.clerk.render.hooks :as-alias hooks]
   [nextjournal.clerk.viewer :as v]))

;; Trying various ways to support jump to definition
(def my-code-viewer
  {:render-fn 'nextjournal.clerk.render/render-code :transform-fn clerk/mark-presented})


(v/->viewer-fn '(fn [spec] (let [plotly (hooks/use-d3-require "plotly.js-dist@2.15.1")
                                 ref-fn (hooks/use-callback #(when % (.newPlot plotly % (clj->js spec ))) [spec plotly])]
                             (js/console.log "dude")
                             (when spec
                               (if plotly
                                 [:div.overflow-x-auto [:div.plotly {:ref ref-fn}]]
                                 render/default-loading-view)))))

(v/->viewer-eval '(fn [spec] (let [plotly (hooks/use-d3-require "plotly.js-dist@2.15.1")
                                   ref-fn (hooks/use-callback #(when % (.newPlot plotly % (clj->js spec))) [spec plotly])]
                               (js/console.log "dude")
                               (when spec
                                 (if plotly
                                   [:div.overflow-x-auto [:div.plotly {:ref ref-fn}]]
                                   render/default-loading-view)))))
