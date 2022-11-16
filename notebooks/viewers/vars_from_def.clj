;; # Var Unwrapping

(ns viewers.vars-from-def
  (:require [nextjournal.clerk :as clerk]))

;; There's currently an incosistency regarding automatically
;; unwrapping `::clerk/var-from-def`:

;; If the viewer is a function, we automatically unwrap it:

^{::clerk/viewer #(clerk/with-viewer {:transform-fn (clerk/update-val pr-str)} %)}
(def foo :bar)

;; If the viewer is a map, we don't:

^{::clerk/viewer {:transform-fn (clerk/update-val pr-str)}}
(def foo-2 :bar)

;; This was a kludge to allow to specify viewers on the value of vars, e.g.

^{::clerk/viewer clerk/table}
(def my-table [[1 2] [3 4]])


;; I think we should unwrap the var value always, unless you opt out of it, maybe via:

^{::clerk/viewer {:transform-fn (clerk/update-val pr-str)
                  ::clerk/var-from-def? true}}
(def foo-2 :bar)


^{::clerk/viewer {:transform-fn (clerk/update-val pr-str)}}
(def foo-2 :bar)

;; Maybe the need for this goes away once we give the predicate
;; functions more context to act on.
