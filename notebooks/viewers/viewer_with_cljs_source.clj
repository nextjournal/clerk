(ns viewers.viewer-with-cljs-source
  (:require [nextjournal.clerk :as clerk]))

(clerk/reg-sci-snippet!
 (pr-str '(do (ns viewers.viewer-with-cljs-source)
              (defn my-already-defined-function [{:keys [x]}]
                (prn :x x)
                x))))

(def my-cool-viewer
  {:render-fn `my-already-defined-function
   :transform-fn (fn [x] x)})
