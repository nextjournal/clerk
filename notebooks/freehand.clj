;; # ✍️ Freehand Drawings
^{:nextjournal.clerk/visibility :hide-ns}
(ns freehand
  (:require [babashka.fs :as fs]
            [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as v]))


^{::clerk/no-cache true ::clerk/viewer :hide-result #_ (partial clerk/with-viewer (assoc v/code-viewer :render-fn '(fn [src] (v/html [:pre src]))))}
(def cljs-code (slurp "notebooks/drawing.cljs"))

(clerk/eval-cljs-str cljs-code)

^{::clerk/visibility :fold}
(def freehand-drawing
  "An utility function to open a freehand drawing cell"
  (partial clerk/with-viewer
           {:render-fn 'drawing/freehand
            :transform-fn (comp v/mark-presented
                                (v/update-val (fn [{:as opts :keys [store-at]}]
                                                (cond-> opts
                                                  (fs/exists? store-at)
                                                  (assoc :svg (slurp store-at))))))}))

(freehand-drawing {:store-at "data/test13.svg"})

^{::clerk/visibility :hide ::clerk/viewer :hide-result}
(comment
  (let [store-at "here"] `(fn [svg#] (spit ~store-at svg#)))
  )
