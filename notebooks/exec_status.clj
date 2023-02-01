;; # 💈 Execution Status
(ns exec-status
  (:require [nextjournal.clerk :as clerk]))

(defn exec-status [{:keys [progress status]}]
  [:div.w-full.bg-purple-200 {:class "h-0.5"}
   [:div.bg-purple-600 {:class "h-0.5" :style {:width (str (* progress 100) "%")}}]
   [:div.absolute.text-purple-600.text-xs.font-sans {:style {:font-size "0.5rem"}} status]])


^{::clerk/width :full ::clerk/viewer clerk/html}
(exec-status {:progress 0 :status "Parsing…"})

^{::clerk/width :full ::clerk/viewer clerk/html}
(exec-status {:progress 0.15 :status "Analyzing…"})

^{::clerk/width :full ::clerk/viewer clerk/html}
(exec-status {:progress 0.55 :status "Evaluating…"})
