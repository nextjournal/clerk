;; # 👋 Hello CLJS
(ns hello-cljs
  (:require [nextjournal.clerk.sci-viewer :as v]))

;; this is _prose_
(v/plotly {:data [{:y (shuffle (range 10)) :name "The Federation"}
                  {:y (shuffle (range 10)) :name "The Empire"}]})

(v/table {:a [1 2 3] :b [4 5 6]})

(v/html [:h1 "🧨"])


(defn fold [f i xs]
  (if (seq xs)
    (fold f (f i (first xs)) (rest xs))
    i))

(fold str "" (range 10))
