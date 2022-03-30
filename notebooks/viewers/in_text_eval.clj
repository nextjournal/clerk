;; #  📝 In-Text Evaluation
^{:nextjournal.clerk/visibility #{:hide-ns :hide}}
(ns ^{:nextjournal.clerk/no-cache true} viewers.in-text-eval
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as v]
            [nextjournal.markdown.transform :as markdown.transform]))

;; Being able to override markdown viewers allows we get in-text evaluation for free:

^{::clerk/viewer clerk/hide-result}
(defonce num★ (atom 3))
#_(reset! num★ 3)

^{::clerk/visibility :show}
(clerk/set-viewers! [{:name :nextjournal.markdown/monospace
                      :transform-fn (comp eval read-string markdown.transform/->text)}
                     {:name :nextjournal.markdown/ruler
                      :transform-fn (constantly
                                     (v/with-viewer :html [:div.text-center (repeat @num★ "★")]))}])
;; ---
^{::clerk/viewer clerk/hide-result}
(defn slider [var {:keys [min max]}]
  (clerk/with-viewer
    {:fetch-fn (fn [_ x] x)
     :transform-fn (fn [var] {:var-name (symbol var) :value @@var})
     :render-fn `(fn [{:as x :keys [var-name value]}]
                   (v/html [:input {:type :range
                                    :min ~min :max ~max
                                    :value value
                                    :on-change #(v/clerk-eval `(reset! ~var-name (Integer/parseInt ~(.. % -target -value))))}]))}
    var))

;; Drag the following slider `(slider #'num★ {:min 1 :max 40})` to control the number of stars (currently **`(deref num★)`**) in our custom horizontal rules.

;; ---
