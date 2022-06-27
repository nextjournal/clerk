;; # 🤹‍♀️ Interactivity
^{:nextjournal.clerk/visibility :hide}
(ns interactivity
  (:require [nextjournal.clerk :as clerk]))

;; Let's try a little Slider using `::clerk/viewers` 🎚


^{::clerk/viewers [{:pred ::clerk/var-from-def
                    :transform-fn (comp clerk/mark-presented (clerk/update-val (fn [{::clerk/keys [var-from-def]}]
                                                                                 {:var-name (symbol var-from-def) :value @@var-from-def})))
                    :render-fn '(fn [{:keys [var-name value]}]
                                  (v/html [:input {:type :range
                                                   :initial-value value
                                                   :on-change #(v/clerk-eval `(reset! ~var-name (Integer/parseInt ~(.. % -target -value))))}]))}]}
(defonce slider-state (atom 42))

@slider-state

;; And a second one using `::clerk/viewer` 🎚
^{::clerk/viewer {:transform-fn (comp clerk/mark-presented (clerk/update-val (fn [{:as x ::clerk/keys [var-from-def]}]
                                                                               {:var-name (symbol var-from-def) :value @@var-from-def})))
                  :render-fn '(fn [{:as x :keys [var-name value]}]
                                (v/html [:input {:type :range
                                                 :initial-value value
                                                 :on-change #(v/clerk-eval `(reset! ~var-name (Integer/parseInt ~(.. % -target -value))))}]))}}
(defonce slider-2-state (atom 42))

@slider-2-state

;; What's the sum of our two sliders?
(+ @slider-state @slider-2-state)

;; And a text box.

^{::clerk/viewer {:pred #(when-let [v (get % ::clerk/var-from-def)]
                           (and v (instance? clojure.lang.IDeref (deref v))))
                  :transform-fn (comp clerk/mark-presented (clerk/update-val (fn [{::clerk/keys [var-from-def]}]
                                                                               {:var-name (symbol var-from-def) :value @@var-from-def})))
                  :render-fn '(fn [{:keys [var-name value]}]
                                (v/html [:input {:type :text
                                                 :placeholder "Schreib mal"
                                                 :initial-value value
                                                 :class "px-3 py-3 placeholder-blueGray-300 text-blueGray-600 relative bg-white bg-white rounded text-sm border border-blueGray-300 outline-none focus:outline-none focus:ring w-full"
                                                 :on-input #(v/clerk-eval `(reset! ~var-name ~(.. % -target -value)))}]))}}
(defonce text-state (atom ""))


@text-state

;; ### TODO
;; - [x] Fix our defonce handling, indepdendently of interactity
;; - [ ] Add built-in viewers to viewers namespace
;; - [x] Improve performance
