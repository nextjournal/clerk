;; # 💈 Execution Status
(ns exec-status
  (:require [nextjournal.clerk :as clerk]))

(def progress-viewer
  {:pred (every-pred map? #(contains? % :progress))
   :render-fn 'nextjournal.clerk.render/exec-status
   :transform-fn (fn [wrapped-value]
                   (-> wrapped-value
                       clerk/mark-presented
                       (assoc :nextjournal/width :full)))})

(clerk/add-viewers! [progress-viewer])

{:progress 0 :status "Parsing…"}

{:progress 0.15 :status "Analyzing…"}

{:progress 0.55 :status "Evaluating…"}
