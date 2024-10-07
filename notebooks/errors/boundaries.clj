(ns boundaries
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as v]))

(clerk/add-viewers! [(assoc v/code-block-viewer :render-fn '(fn [] borked))])

(clerk/with-viewer
  {:require-cljs true
   :render-fn 'errors.render/test}
  [1 2 3])
