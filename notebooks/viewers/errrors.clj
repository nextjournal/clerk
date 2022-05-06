;; # Viewer Errors 💣💥
;; Different ways that viewers can blow up.
(ns errors
  (:require [nextjournal.clerk :as clerk]))

;; An undefined symbol in a `:render-fn` will error on `->viewer-fn` during read.
(clerk/with-viewer {:render-fn '(fn [x] boom)}
  42)

;; If `->viewer-fn` succeeds, it can still blow up at render time.
(clerk/with-viewer {:render-fn '(fn [x] (throw (ex-info "I blow up when called" {})))}
  :boom)
