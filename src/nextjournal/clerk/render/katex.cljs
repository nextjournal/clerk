(ns nextjournal.clerk.render.katex
  (:require ["katex" :as katex]))

(defn renderToString [s]
  (katex/renderToString s))
