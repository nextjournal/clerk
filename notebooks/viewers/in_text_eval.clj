;; #  📝 _In-Text_ Evaluation
^{:nextjournal.clerk/visibility #{:hide-ns}}
(ns ^{:nextjournal.clerk/no-cache true} viewers.in-text-eval
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as v]
            [viewers.custom-markdown :as custom-md]
            [nextjournal.markdown.transform :as markdown.transform]))

;; Being able to override markdown viewers allows us to get in-text evaluation for free:

(defonce num★ (atom 3))

(def md-eval-viewers
  [{:name :nextjournal.markdown/monospace
    :transform-fn (comp eval read-string markdown.transform/->text v/->value)}
   {:name :nextjournal.markdown/ruler
    :transform-fn (constantly (v/with-viewer :html [:div.text-center (repeat @num★ "★")]))}])

^{::clerk/visibility :hide ::clerk/viewer :hide-result}
(def viewers-with-md-eval
  (v/update-viewers (v/get-default-viewers) {(comp #{:markdown} :name)
                                             (custom-md/update-child-viewers #(v/add-viewers % md-eval-viewers))}))

^{::clerk/viewer clerk/hide-result}
(v/reset-viewers! viewers-with-md-eval)

;; ---

^{::clerk/viewer clerk/hide-result ::clerk/visibility :hide}
(defn slider [var {:keys [min max]}]
  (clerk/with-viewer
   {:name :slider
    :transform-fn (fn [wrapped-value]
                    (-> wrapped-value
                        #_ (assoc :nextjournal/value "foo")
                        (update :nextjournal/value (fn [var] {:var-name (symbol var) :value @@var}))
                        (assoc :nextjournal/reduced? true)))
    :render-fn `(fn [{:keys [var-name value]}]
                  (v/html [:input {:type :range
                                   :min ~min :max ~max
                                   :value value
                                   :on-change #(v/clerk-eval `(reset! ~var-name (Integer/parseInt ~(.. % -target -value))))}]))}
    var))

;; Drag the following slider `(slider #'num★ {:min 1 :max 40})` to control the number of stars (currently **`(deref num★)`**) in our custom horizontal rules.

;; ---
