;; # 💡 Alias Resolution

;; Until now, we've defined a number of global aliases in Clerk's SCI
;; Environment that evaluates render functions in the browser. These
;; had no connection to the namespace they were defined in and
;; naturally folks where confused what things like `v/html` meant.

;; We've since migrated all `:render-fn`s in Clerk to fully qualified
;; symbols to unambigously identity a function & support jump to definition.


;; Clojure 1.11 introduced `:as-alias` to support defining an alias
;; without loading the corresponding namespace. Here we use it to
;; define an alias for Clerk's rendering namespaces (which are written
;; in ClojureScript).

(ns render-aliases
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.clerk.render :as-alias render]
            [nextjournal.clerk.render.hooks :as-alias hooks]))

;; Note how we have an alias defined for `render` and `hooks`.

(ns-aliases *ns*)

;; When you don't want to type out the full aliases, you can use
;; `clerk/resolve-aliases` on a `render-fn` and it will perform the
;; alias resolution for you. Note how `hooks/` in the code has been
;; resolved into `nextjournal.clerk.render.hooks/`.

(def plotly-viewer
  {:transform-fn clerk/mark-presented
   :render-fn #_clerk/resolve-aliases
   '(fn [spec]
      (let [plotly (hooks/use-d3-require "plotly.js-dist@2.15.1")
            ref-fn (hooks/use-callback #(when % (.newPlot plotly % (clj->js spec))) [spec plotly])]
        (when spec
          (if plotly
            [:div.overflow-x-auto [:div.plotly {:ref ref-fn}]]
            render/default-loading-view))))})

(clerk/with-viewer plotly-viewer
  {:layout {:title "A surface plot"}
   :data [{:z [[1 2 3] [3 2 1]]
           :type "surface"}]})
