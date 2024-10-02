;; # ⚡️ Global Sync
(ns global-sync
  (:require [nextjournal.clerk :as clerk]))

^::clerk/no-cache
(shuffle (range 100))

(clerk/with-viewer {:render-fn
                    '(fn [_ opts]
                       [:div
                        [:button
                         {:on-click #(swap! nextjournal.clerk.render/!sync-state assoc :foo (rand-int 1000))}
                         "clickme"]
                        (pr-str @nextjournal.clerk.render/!sync-state)
                        #_(nextjournal.clerk.render/inspect @nextjournal.clerk.render/!sync-state)])}
  {})

#_(swap! nextjournal.clerk.webserver/!sync-state assoc :foo (rand-int 1000))
