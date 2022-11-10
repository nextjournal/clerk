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

(clerk/with-viewer {:render-fn '(fn [_] (v/inspect-presented :crash))}
  42)

(clerk/with-viewer {:render-fn '(fn [_] (v/html (v/inspect-presented :crash)))}
  42)

(clerk/with-viewer {:render-fn '(fn [_] (v/html [1 2 3]))}
  42)


(clerk/with-viewer {:render-fn '(fn [x] (let [handle-error (nextjournal.clerk.render/use-error-handler)
                                              ref(nextjournal.clerk.render/use-callback (fn [_]
                                                                                          (-> (js/Promise. (fn [resolve reject]
                                                                                                             (js/setTimeout (fn [] (resolve 1)), 200)))
                                                                                              (.then (fn [x] (throw (js/Error "async error 💥"))))
                                                                                              (.catch handle-error))))]
                                          [:h3 {:ref ref} (str x)]))}
  42)
