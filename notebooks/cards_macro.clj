(ns cards-macro
  (:require [nextjournal.clerk.viewer :as v]))

(defmacro card [body] `(v/->viewer-eval '~(v/resolve-aliases body)))
