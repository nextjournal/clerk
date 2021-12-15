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
  (or (defonce state (atom 42)) #'state))

#_(ns-unmap *ns* 'state)

@state


;; ### TODO
;; - [x] Fix our defonce handling, indepdendently of interactity
;; - [ ] Add built-in viewers to viewers namespace
;; - [ ] Improve performance
