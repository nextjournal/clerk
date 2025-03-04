(ns macro-auto-gensym
  (:require [macro-with-auto-gensym :as m]))

;; Q: when I change my/macro and re-eval the notebook, should it render the change?

(inc (m/my-macro 1))
