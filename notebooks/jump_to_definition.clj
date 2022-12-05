;; # 🤾🏼 Jump to Definition
(ns jump-to-definition
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as viewer]
            [clojure.string :as str]
            [nextjournal.clerk.render :as-alias render]))

;; Trying various ways to support jump to definition
(clerk/code (slurp ".clj-kondo/hooks.clj"))

(viewer/->ViewerFn '(fn [x opts]
                      [nextjournal.clerk.render/render-code x]))

(viewer/->ViewerFn 'nextjournal.clerk.render/render-code)

(viewer/->ViewerFn `render/render-code)