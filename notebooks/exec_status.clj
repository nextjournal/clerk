;; # 💈 Execution Status
(ns exec-status
  (:require [nextjournal.clerk :as clerk]))

(def progress-viewer
  {:pred (every-pred map? #(contains? % :progress))
   :render-fn '(fn exec-status [{:keys [progress status]}]
                 [:div.w-full.bg-purple-200 {:class "h-0.5"}
                  [:div.bg-purple-600 {:class "h-0.5" :style {:width (str (* progress 100) "%")}}]
                  [:div.absolute.text-purple-600.text-xs.font-sans {:style {:font-size "0.5rem"}} status]])
   :transform-fn (fn [wrapped-value]
                   (-> wrapped-value
                       clerk/mark-presented
                       (assoc :nextjournal/width :full)))})

(clerk/add-viewers! [progress-viewer])

{:progress 0 :status "Parsing…"}

{:progress 0.15 :status "Analyzing…"}

{:progress 0.55 :status "Evaluating…"}

(do
  (Thread/sleep 1000))
