;; # 🔄 Sync Troubles
(ns sync-troubles
  {:nextjournal.clerk/no-cache true}
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as v]))

;; ## Non atoms
;; non-atoms are just ignored, but maybe we should complain

^{::clerk/sync true}
(def syncrash-0 "hey")

;; ## Invalid values in Atoms
;; ### Functions
^{::clerk/sync true}
(defonce syncrash-2 (atom (fn [x] "whatever")))
;; blank screen of death (after reload) / browser console:
;;
;;     uncaught error:
;;     {:message "Invalid symbol: scratch.sync-troubles/eval97899/fn--97900"...


;; ### Objects
^{::clerk/sync true}
(defonce syncrash-3 (atom (Object.)))
;; object viewer displays an atom but console reports
;;
;;     error in viewer-eval
;;     :message "Could not resolve symbol: object"

;; ## Syncing disallowed values from the client
^{::clerk/sync true
  ::clerk/viewer (assoc v/viewer-eval-viewer
                        :render-fn '(fn [x] [:h1 {:on-click #(reset! x (js/Object.))} @x]))}
(defonce syncrash-4 (atom "test"))
