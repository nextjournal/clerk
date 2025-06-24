(ns nextjournal.clerk.test-utils
  (:require [clojure.test :as t]))


(def timer (atom nil))

(defmethod t/report :begin-test-var [m]
  (binding [*out* *err*]
    (println "===" (-> m :var meta :name))
    (reset! timer (. System (nanoTime)))))

(defmethod t/report :end-test-var [_m]
  (println (str "Elapsed time: " (/ (double (- (. System (nanoTime)) @timer)) 1000000.0) " msecs"))
  (println)
  (when-let [rc t/*report-counters*]
    (when-let [{:keys [:fail :error]} @rc]
      (when (and (= "true" (System/getenv "FAIL_FAST"))
                 (or (pos? fail) (pos? error)))
        (binding [*out* *err*]
          (println "=== Failing fast"))
        (System/exit 1)))))
