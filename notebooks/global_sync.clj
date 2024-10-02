;; # ⚡️ Global Sync
(ns global-sync
  (:require [nextjournal.clerk :as clerk]))

^::clerk/no-cache
(shuffle (range 100))

#_(swap! nextjournal.clerk.webserver/!sync-state assoc :foo (rand-int 1000))
