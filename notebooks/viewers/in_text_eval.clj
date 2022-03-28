;; #  📝 In-Text Evaluation
^{:nextjournal.clerk/visibility :hide-ns}
(ns ^{:nextjournal.clerk/no-cache true} viewers.custom-markdown
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as v]
            [nextjournal.markdown.transform :as markdown.transform]))

;; Being able to override markdown viewers allows we get in-text evaluation for free:

(defonce N✨ (atom 42))

(clerk/set-viewers! [{:name :nextjournal.markdown/monospace
                      :transform-fn (comp eval read-string markdown.transform/->text)}
                     {:name :nextjournal.markdown/ruler
                      :transform-fn (constantly
                                     (v/with-viewer :html [:span (repeat @N✨ "✨")]))}])
;; ---
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

;; This slider `(slider #'N✨ {:min 10 :max 50})` controls the numebr (**`(deref N✨)`**) of stars in our custom horizontal rules.

;; ---
