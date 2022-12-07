(ns cards-macro
  (:require [nextjournal.clerk.viewer :as v]))

(defmacro card
  ([body] `(v/->viewer-eval '~body))
  ([opts body] (v/with-viewer {:tranform-fn identity} opts `(v/->viewer-eval '~body))))
