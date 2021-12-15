(ns ^:nextjournal.clerk/no-cache notebooks.test
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as clerk.viewer]))

(clerk/html
  [:h1 (clerk.viewer/->SCIEval '(str (+ 40 1)))])


