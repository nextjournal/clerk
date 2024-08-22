(ns nextjournal.clerk.fixtures.issue-660-specter-repro
  {:nextjournal.clerk/error-on-missing-vars :off}
  (:require [com.rpl.specter :as sr]))

(defn sample-specter-fn [x] (sr/select [sr/ALL] x))
