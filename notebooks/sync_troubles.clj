;; # Viewers as Functions
(ns sync-troubles
  {:nextjournal.clerk/no-cache true}
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as v]))

;; ## Non atoms
;; non-atoms are just ignored, but maybe we should complain
^{::clerk/sync true}
(def syncrash-0 "hey")

;; ## Invalid values in Atoms
;; ### Symbols
^{::clerk/sync true ::clerk/viewer v/viewer-eval-viewer}
(defonce syncrash-1 (atom 'ahoi))
;; browser console:
;;
;;    (caught) error in viewer-eval
;;    `{:message "Could not resolve symbol: ahoi", ...}`
;;
;;
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
                        :render-fn '(fn [x] (reset! x 'crashme) [:h1 @x]))}
(defonce syncrash-4 (atom "test"))
;; crashes on the JVM:
;;
;;    Caused by: java.lang.RuntimeException: Unable to resolve symbol: crashme in this context
;;   	at clojure.lang.Util.runtimeException(Util.java:221)
;;   	at clojure.lang.Compiler.resolveIn(Compiler.java:7431)
;;   	at clojure.lang.Compiler.resolve(Compiler.java:7375)
