;; # Katex

(ns katex
  (:require [nextjournal.clerk]
            [nextjournal.clerk.viewer]))

;; Inline formula: $x^2$


;; Block level formula:
;; $$x^2$$

;; Manual viewer:
(nextjournal.clerk/with-viewer
  nextjournal.clerk.viewer/katex-viewer "x^2")

