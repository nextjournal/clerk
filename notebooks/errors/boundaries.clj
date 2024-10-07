(ns boundaries
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as v]))

(clerk/add-viewers! [(assoc v/code-block-viewer :render-fn '(fn [] borked))])
