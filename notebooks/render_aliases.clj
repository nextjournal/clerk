;; # 💡 Alias Resolution

;; Until now, we've defined a number of global aliases in Clerk's SCI
;; Environment that evaluates render functions in the browser. These
;; had no connection to the namespace they were defined in and
;; naturally folks where confused what things like `v/html` meant.

(ns render-aliases
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.clerk.render :as-alias render]
            [nextjournal.clerk.render.hooks :as-alias hooks]))

;; Clerk now uses the `ns-aliases` defined in the JVM to resolve
;; namespaced symbols, making this conection clear and even enabling
;; jump to definition. In Clojure 1.11 this works nicely with
;; `:as-alias` support allowing us to define aliases for namespaces
;; that are ClojureScript only.
(ns-aliases *ns*)

;; Using this, we can define fairly complex viewers like plotly
;; completely in userspace while keeping things understandable.
(def plotly-viewer
  {:transform-fn clerk/mark-presented
   :render-fn '(fn [spec] (let [plotly (hooks/use-d3-require "plotly.js-dist@2.15.1")
                                ref-fn (hooks/use-callback #(when % (.newPlot plotly % (clj->js spec))) [spec plotly])]
                            (when spec
                              (if plotly
                                [:div.overflow-x-auto [:div.plotly {:ref ref-fn}]]
                                render/default-loading-view))))})

(clerk/with-viewer plotly-viewer
  {:layout {:title "A surface plot"}
   :data [{:z [[1 2 3] [3 2 1]]
           :type "surface"}]})
