;; # Squint islands
;; Run with `(clerk/serve! {:frontend :datastar-squint-island})`.
(ns datastar-squint
  (:require [nextjournal.clerk :as clerk]))

(clerk/with-viewer {:render-fn '(fn [x] [:div.text-red-500.font-bold (str "Hello, " x)])}
  "Squint")

(clerk/with-viewer {:transform-fn clerk/mark-presented
                    :render-fn '#(vector :ul (for [i %] [:li.font-mono (str i " squared is " (* i i))]))}
  (range 5))

(clerk/with-viewer {:transform-fn clerk/mark-presented
                    :render-fn '(fn [{:keys [width height]} _]
                                  [:svg {:width width :height height}
                                   [:circle {:cx (/ width 2) :cy (/ height 2) :r (/ height 3) :fill "teal"}]])}
  {:width 200 :height 100})

(clerk/with-viewer {:render-fn '(fn [x] [:button.px-3.py-1.rounded.bg-indigo-600.text-white
                                         {:on-click (fn [e] (set! (.. e -target -textContent) (str "clicked " x)))}
                                         "click me"])}
  42)

(clerk/with-viewer {:render-fn '(fn [_] (throw (js/Error. "I blow up in the browser")))}
  :boom)

;; A `:render-fn` using symbols outside `clojure.core` and `js/` throws at the REPL:
#_(clerk/with-viewer {:render-fn '(fn [x] [nextjournal.clerk.render/inspect x])} 1)
