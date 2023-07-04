;; # 💈 Execution Status
(ns exec-status
  {:nextjournal.clerk/toc true}
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.clerk.webserver :as webserver]))

;; To see what's going on while waiting for a long-running
;; computation, Clerk will now show an execution status bar on the
;; top. For named cells (defining a var) it will show the name of the
;; var, for anonymous expressions, a preview of the form.

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

{:progress 0.55 :cell-progress 0.34 :status "Evaluating…"}

{:progress 0.95 :status "Presenting…"}

(defn set-cell-progress! [progress]
  (swap! webserver/!session->doc update nil (fn [doc] (if-let [status (-> doc meta :status)]
                                                        (let [status+progress (assoc status :cell-progress progress)]
                                                          (when-let [send-future (-> doc meta ::webserver/!send-status-future)]
                                                            (future-cancel send-future))
                                                          (webserver/broadcast-status! status+progress)
                                                          (-> doc
                                                              (vary-meta dissoc ::!send-status-future)
                                                              (vary-meta assoc :status status+progress)))
                                                        doc))))

(defonce !rand
  (atom 0))

^::clerk/no-cache (reset! !rand (rand-int 100))
(Thread/sleep (+ 2000 @!rand))

(def sleepy-cell
  (let [total (+ 2001 @!rand)]
    (doseq [i (range total)]
      (do
        (Thread/sleep 10)
        (set-cell-progress! (/ i (float total)))))))

(Thread/sleep (+ 2002 @!rand))

(def sleepy-cell-2
  (Thread/sleep (+ 2003 @!rand)))


