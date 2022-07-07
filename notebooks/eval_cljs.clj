;; # 📑 Eval CLJS
(ns eval-cljs
  (:require [nextjournal.clerk :as clerk]))

;; Let's load some .cljs code from a file!
;; Because we want to re-render this notebook when the .cljs code changes, we use `::clerk/no-cache`
;; and set `clerk/code` as the viewer so we can see the code:

^{::clerk/no-cache true ::clerk/viewer clerk/code}
(def cljs-code (slurp "notebooks/render_fns.cljs"))

;; Let's evaluate the CLJS code on the client:
(clerk/eval-cljs-str cljs-code)

;; And now let's use those functions!

(clerk/with-viewer {:render-fn 'render-fns/heading}
  "My awesome heading")

(clerk/with-viewer {:render-fn 'render-fns/paragraph}
  "My awesome paragraph :)")


;; Now let's try to figure out a way so our slider can still be dragged:
^{::clerk/viewers [{:pred ::clerk/var-from-def
                    :transform-fn (comp clerk/mark-presented (clerk/update-val (fn [{::clerk/keys [var-from-def]}]
                                                                                 {:var-name (symbol var-from-def) :value @@var-from-def})))
                    :render-fn '(fn [{:keys [var-name value]}]
                                  (v/html [:input {:type :range
                                                   :value value
                                                   :on-change #(v/clerk-eval `(reset! ~var-name (Integer/parseInt ~(.. % -target -value))))}]))}]}
(defonce slider-state (atom 42))

@slider-state
