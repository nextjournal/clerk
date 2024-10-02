;; # ⚡️ Global Sync
(ns global-sync
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as viewer]))

^::clerk/no-cache
(shuffle (range 100))

(clerk/with-viewer {:render-fn
                    '(fn [_ opts]
                       [:div
                        [:button
                         {:on-click #(swap! nextjournal.clerk.viewer/!sync-state assoc :foo (rand-int 1000))}
                         "clickme"]
                        [nextjournal.clerk.render/inspect @nextjournal.clerk.viewer/!sync-state]
                        #_(nextjournal.clerk.render/inspect @nextjournal.clerk.render/!sync-state)])}
  {})

@viewer/!sync-state

#_(swap! nextjournal.clerk.viewer/!sync-state assoc :foo (rand-int 1000))
