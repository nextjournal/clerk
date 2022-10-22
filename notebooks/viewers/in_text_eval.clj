;; #  📝 _In-Text_ Evaluation
^{:nextjournal.clerk/visibility {:code :hide}}
(ns ^{:nextjournal.clerk/no-cache true} viewers.in-text-eval
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as v]
            [viewers.custom-markdown :as custom-md]
            [nextjournal.markdown.transform :as markdown.transform]))

;; Being able to override markdown viewers allows us to get in-text evaluation for free:

(defonce num★ (atom 20))

(def md-eval-viewers
  [{:name :nextjournal.markdown/monospace
    :transform-fn (comp eval read-string markdown.transform/->text v/->value)}
   {:name :nextjournal.markdown/ruler
    :transform-fn (constantly (v/with-viewer :html [:div.text-center (repeat @num★ "★")]))}])

^{::clerk/visibility {:result :hide}}
(def viewers-with-md-eval
  (v/update-viewers (v/get-default-viewers) {(comp #{:markdown} :name)
                                             (custom-md/update-child-viewers #(v/add-viewers % md-eval-viewers))}))

^{::clerk/visibility {:result :hide}}
(clerk/reset-viewers! viewers-with-md-eval) ;; register viewer globally for ns

;; ---

^{::clerk/visibility {:result :hide}}
(defn slider [var {:keys [min max]}]
  (clerk/with-viewer
    {:transform-fn (comp v/mark-presented (v/update-val (fn [var] {:var-name (symbol var) :value @@var})))
     :render-fn `(fn [{:keys [var-name value]}]
                   [:input {:type :range :min ~min :max ~max :value value
                            :on-change #(v/clerk-eval `(reset! ~var-name (Integer/parseInt ~(.. % -target -value))))}])}
    var))

;; Drag the following slider `(slider #'num★ {:min 1 :max 44})` to control the number of stars (currently **`(deref num★)`**) in our custom horizontal rules.

;; ---
