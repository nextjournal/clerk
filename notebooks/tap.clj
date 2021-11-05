;; # 🚰 Tap Inspector
(ns tap
  (:require [nextjournal.clerk :as clerk]))

(defonce !taps (atom ()))

^::clerk/no-cache @!taps

(defn tapped [x]
  (binding [*ns* (find-ns 'user)]
    (swap! !taps conj x)
    (clerk/show! "notebooks/tap.clj")))

(defonce setup
  (add-tap tapped))

#_(tap> (rand-int 1000))

#_(do (reset! !taps ()) (clerk/show! "notebooks/tap.clj"))
