(ns viewers.viewer-with-cljs-source)

(def my-cool-viewer
  {:render-fn 'viewers.viewer-with-cljs-source/my-already-defined-function2
   :require-cljs 'viewers.viewer-with-cljs-source
   :transform-fn (fn [x] x)})
