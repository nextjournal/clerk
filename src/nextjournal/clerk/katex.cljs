(ns nextjournal.clerk.katex
  (:require ["katex" :as katex]))

(defn renderToString [s]
  (katex/renderToString s))
