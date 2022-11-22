;; # Vars from definitions
(ns viewers.var-from-def
  {:nextjournal.clerk/no-cache true}
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as v]))
;; By default, for def-like expressions we're showing the value of the underlying variable.
(def d1 "def1")

(defonce d2 "def2")

^{::clerk/viewer {:render-fn '(fn [x] [:pre (pr-str x)])}}
(def d3 "def3")

;; We can opt out of the default behavour and obtain a var wraapped in a map like: `{::clerk/var-from-def my-var}` by setting a truthy `:var-from-def?` on the viewer map:

(def var-wrapping-viewer
  {:var-from-def? true
   :render-fn '(fn [x] [:pre (pr-str x)])})

^{::clerk/viewer var-wrapping-viewer}
(def d4 "def4")

^{::clerk/viewer (assoc var-wrapping-viewer :transform-fn (v/update-val (comp meta ::clerk/var-from-def)))}
(def ^{:foo 'bar} d5 "def5")
