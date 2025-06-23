(ns nextjournal.clerk.test-utils
  (:require [clojure.test :as t]))


(defmethod t/report :begin-test-var [m]
  (binding [*out* *err*]
    (println "===" (-> m :var meta :name))
    (println)))

(defmethod t/report :end-test-var [_m]
  (when-let [rc t/*report-counters*]
    (when-let [{:keys [:fail :error]} @rc]
      (when (and (= "true" (System/getenv "FAIL_FAST"))
                 (or (pos? fail) (pos? error)))
        (binding [*out* *err*]
          (println "=== Failing fast"))
        (System/exit 1)))))
