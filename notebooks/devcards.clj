(ns devcards
  (:require [nextjournal.clerk.viewer :as v]))

(def card-viewer
  {:transform-fn (comp v/mark-presented (v/update-val v/->viewer-eval))
   :render-fn '(fn [data]
                 (if (v/valid-react-element? data) data (v/html [v/inspect data])))})

(defmacro defcard [body] `(v/with-viewer card-viewer '~body))
