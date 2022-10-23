(ns cards-macro
  (:require [nextjournal.clerk.viewer :as v]))

(def card-viewer
  {:transform-fn (comp v/mark-presented (v/update-val v/->viewer-eval))
   :render-fn '(fn [data]
                 (if (nextjournal.clerk.render/valid-react-element? data)
                   data
                   [nextjournal.clerk.render/inspect data]))})

(defmacro card [body] `(v/with-viewer card-viewer '~body))
