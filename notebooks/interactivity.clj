;; # 🤹‍♀️ Interactivity
^{:nextjournal.clerk/visibility :hide-ns}
(ns ^:nextjournal.clerk/no-cache interactivity
  (:require [nextjournal.clerk :as clerk]))


;; Let's try a little Slider 🎚
^{::clerk/visibility :fold}
(clerk/with-viewers [{:pred (fn [x] (and (var? x) (instance? clojure.lang.IDeref (deref x))))
                      :fetch-fn (fn [_ x] x)
                      :transform-fn (fn [var]
                                      {:var-name (symbol var) :value @@var})
                      :render-fn (fn [{:keys [var-name value]}]
                                   (v/html [:input {:type :range
                                                    :value value
                                                    :on-change #(v/clerk-eval
                                                                 `(do
                                                                    (reset! ~var-name (Integer/parseInt ~(.. % -target -value)))
                                                                    (nextjournal.clerk/show! @nextjournal.clerk/!last-file)))}]))}]
  (or (defonce slider-state (atom 42)) #'slider-state))

#_(ns-unmap *ns* 'slider-state)

@slider-state

^{::clerk/visibility :fold}
(clerk/with-viewers [{:pred (fn [x] (and (var? x) (instance? clojure.lang.IDeref (deref x))))
                      :fetch-fn (fn [_ x] x)
                      :transform-fn (fn [var]
                                      {:var-name (symbol var) :value @@var})
                      :render-fn (fn [{:keys [var-name value]}]
                                   (v/html [:input {:type :text
                                                    :placeholder "Schreib mal"
                                                    :class "px-3 py-3 placeholder-blueGray-300 text-blueGray-600 relative bg-white bg-white rounded text-sm border border-blueGray-300 outline-none focus:outline-none focus:ring w-full"
                                                    :on-input #(v/clerk-eval
                                                                `(do
                                                                   (reset! ~var-name ~(.. % -target -value))
                                                                   (nextjournal.clerk/show! @nextjournal.clerk/!last-file)))}]))}]
  (or (defonce text-state (atom "")) #'text-state))

#_ (reset! text-state "")
#_ (ns-unmap *ns* 'text-state)

@text-state

;; ### TODO
;; - [x] Fix our defonce handling, indepdendently of interactity
;; - [ ] Add built-in viewers to viewers namespace
;; - [ ] Improve performance
