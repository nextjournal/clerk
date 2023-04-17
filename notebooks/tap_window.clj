;; # 🪲Debug
(ns notebook.tap-window
  {:nextjournal.clerk/visibility {:code :hide :result :hide}
   :nextjournal.clerk/no-cache true}
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as v]))

(def window-viewer
  {:render-fn '(fn [{:keys [vals]} opts]
                 [nextjournal.clerk.render.window/show
                  (into [:div]
                        (map (fn [v]
                               [:div.mb-4.pb-4.border-b
                                [nextjournal.clerk.render/inspect-presented v]]))
                        (:nextjournal/value vals))])
   :transform-fn v/mark-preserve-keys})

(defonce !taps (atom '()))

(defonce taps-setup (add-tap (fn [x]
                               (swap! !taps conj x)
                               (clerk/recompute!))))

^{::clerk/visibility {:result :show}}
(clerk/with-viewer window-viewer
  {:vals @!taps})

(comment
  (tap> (clerk/html [:div.w-8.h-8.bg-green-500]))
  (tap> (clerk/plotly {:data [{:x [1 2 3 4]}]})))
