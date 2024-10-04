;; # ⚡️ Global Sync
(ns global-sync
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as viewer]))

^::clerk/no-cache
(shuffle (range 100))

(clerk/with-viewer {:render-fn '(fn [_ {:as opts :keys [id path swap-sync-state! !sync-state]}]
                                  [:div
                                   [:button
                                    {:on-click #(swap-sync-state! assoc (into [id] path) (rand-int 1000))}
                                    "clickme"]
                                   [nextjournal.clerk.render/inspect @!sync-state]])}
  {})

@viewer/!sync-state

#_(swap! nextjournal.clerk.viewer/!sync-state assoc :foo (rand-int 1000))
#_(reset! nextjournal.clerk.viewer/!sync-state {})
