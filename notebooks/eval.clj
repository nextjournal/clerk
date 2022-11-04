(ns eval
  (:require [nextjournal.clerk :as clerk]))

(defmacro cljs [& exprs]
  `(clerk/with-viewer {:render-fn '(fn [_ _] [v/inspect (do ~@exprs)])} {} nil))

(cljs nil)