;; # 🐞Debug
(ns nested-matching-pred
  {:nextjournal.clerk/no-cache true}
  (:require [nextjournal.clerk :as clerk]))

(def recursive-viewer
  {:pred :a/b
   :transform-fn (fn [{:as wv :keys [id]}]
                   (when-not id (throw (ex-info "No ID" {:wrapped-value wv})))
                   wv)})


{:a/b 1
 :children [{:a/b 2}]}
