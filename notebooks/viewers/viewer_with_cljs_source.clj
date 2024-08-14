(ns viewers.viewer-with-cljs-source)

(alias 'this 'viewers.viewer-with-cljs-source) ;; an ode to JS this

(def my-cool-viewer
  {:render-fn `this/my-already-defined-function2
   :require-cljs true
   :transform-fn (fn [x] x)})
