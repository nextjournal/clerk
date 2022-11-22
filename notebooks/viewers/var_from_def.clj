;; # Vars from definitions
(ns viewers.var-from-def
  {:nextjournal.clerk/no-cache true}
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as v]))

;; By default, for def-like expressions we're showing the underlying var value.
(def d1 "def1")

(defonce d2 "def2")

^{::clerk/viewer {:render-fn '(fn [x] [:pre (pr-str x)])}}
(def d3 "def3")

;; We can opt-out of the default behavour and obtain a var wraapped in a map with: `{::clerk/var-from-def my-var}` like so:

^{::clerk/viewer {:var-from-def? true
                  :render-fn '(fn [x] [:pre (pr-str x)])}}
(def ^{:foo 'bar} d4 "def4")

^{::clerk/viewer {:var-from-def? true
                  :transform-fn (v/update-val (comp meta ::clerk/var-from-def))}}
(def ^{:foo 'bar} d5 "def5")
