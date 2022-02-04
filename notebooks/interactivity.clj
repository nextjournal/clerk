;; # 🤹‍♀️ Interactivity
^{:nextjournal.clerk/visibility :hide}
(ns ^:nextjournal.clerk/no-cache interactivity
  (:require [nextjournal.clerk :as clerk]))

(clerk/set-viewers! [{:pred #(when-let [v (get % ::clerk/var-from-def)]
                               (and v (instance? clojure.lang.IDeref (deref v))))
                      :fetch-fn (fn [_ x] x)
                      :transform-fn (fn [{::clerk/keys [var-from-def]}]
                                      {:var-name (symbol var-from-def) :value @@var-from-def})
                      :render-fn '(fn [{:keys [var-name value]}]
                                    (v/html (cond (number? value)
                                                  [:input {:type :range
                                                           :initial-value value
                                                           :on-change #(v/clerk-eval `(reset! ~var-name (Integer/parseInt ~(.. % -target -value))))}]
                                                  (string? value)
                                                  (v/html [:input {:type :text
                                                                   :placeholder "Schreib mal"
                                                                   :initial-value value
                                                                   :class "px-3 py-3 placeholder-blueGray-300 text-blueGray-600 relative bg-white bg-white rounded text-sm border border-blueGray-300 outline-none focus:outline-none focus:ring w-full"
                                                                   :on-input #(v/clerk-eval `(reset! ~var-name ~(.. % -target -value)))}]))))}])

;; Let's try a little Slider 🎚
(defonce slider-state (atom 42))

#_(ns-unmap *ns* 'slider-state)

@slider-state

(defonce text-state (atom ""))

#_ (reset! text-state "")
#_ (ns-unmap *ns* 'text-state)

@text-state

;; ### TODO
;; - [x] Fix our defonce handling, indepdendently of interactity
;; - [ ] Add built-in viewers to viewers namespace
;; - [x] Improve performance
