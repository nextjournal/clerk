(ns sync-atom-with-big-data
  (:require [nextjournal.clerk :as clerk]))

;; # Clerk synced atoms and large data

;; **1.** Define a Clerk synced atom called `!state`.

^::clerk/sync
(def !state
  (atom []))

@!state

;; **2.** Verify that `!state` is present on both sides.

^::clerk/no-cache
(clerk/eval-cljs '!state)

;; **3.** Make a button to cram a lot of data into `!state` on the SCI side.

(def large-sync-button-viewer
  {:render-fn '(fn [_]
                 [:button.bg-sky-500.text-white.rounded-xl.px-2.py-1
                  {:on-click (fn [_]
                               (reset! !state
                                       (vec (repeat 20000 {:hi :this
                                                           :is :a
                                                           :large :sync}))))}
                  "Click for large sync!"])})

^{::clerk/viewer large-sync-button-viewer}
{}

;; **4.** Click the button to see what happens 🙂
