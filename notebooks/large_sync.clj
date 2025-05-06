(ns large-atom-sync
  (:require [nextjournal.clerk :as clerk]))

;; # Clerk synced atoms and large data

;; **1.** Define a Clerk synced atom called `!state`.

(def el
  {:hi :this
   :is :a
   :large :sync})

^::clerk/sync
(defonce !state
  (atom (vec (repeat 20000 el))))

;; **2.** Verify that `!state` is present on both sides.
(clerk/eval-cljs '!state)

;; **3.** Make a button to cram a lot of data into `!state` on the SCI side.

^{::clerk/visibility {:code :hide :result :hide}}
(def large-sync-button-viewer
  {:render-fn '(fn [_]
                 [:button.bg-sky-500.text-white.rounded-xl.px-2.py-1
                  
                  {:on-click (fn [_]
                               (swap! large-atom-sync/!state conj {:rand-int (rand-int 42000)}))}
                  "Click for large sync!"])})

^{::clerk/viewer large-sync-button-viewer}
{}

(peek @!state)

;; **4.** Click the button to see what happens 🙂
#_(clerk/recompute!)
