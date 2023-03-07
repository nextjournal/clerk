;; # 💈 Execution Status
(ns exec-status
  (:require [nextjournal.clerk :as clerk]))

(def progress-viewer
  {:pred (every-pred map? #(contains? % :progress))
   :render-fn 'nextjournal.clerk.render/exec-status
   :transform-fn (fn [wrapped-value]
                   (-> wrapped-value
                       clerk/mark-presented
                       (assoc :nextjournal/width :full)))})

#_(def first-sleepy-cell
    (Thread/sleep (+ 2003 @!rand)))

(clerk/add-viewers! [progress-viewer])

{:progress 0 :status "Parsing…"}

{:progress 0.15 :status "Analyzing…"}

{:progress 0.55 :status "Evaluating…"}

{:progress 0.95 :status "Presenting…"}

(defonce !rand
  (atom 0))

^::clerk/no-cache (reset! !rand (rand-int 100))
(Thread/sleep (+ 2000 @!rand))

(def sleepy-cell
  (Thread/sleep (+ 2001 @!rand)))

(Thread/sleep (+ 2002 @!rand))

(def sleepy-cell-2
  (Thread/sleep (+ 2003 @!rand)))
