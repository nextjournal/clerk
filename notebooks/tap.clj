;; # 🚰 Tap Inspector
^{:nextjournal.clerk/visibility :hide}
(ns nextjournal.clerk.tap
  (:require [clojure.core :as core]
            [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as v])
  (:import (java.time Instant LocalTime ZoneId)))

^{::clerk/viewer clerk/hide-result}
(def switch-view
  {:transform-fn (comp clerk/mark-presented
                       (clerk/update-val (fn [{::clerk/keys [var-from-def]}]
                                           {:var-name (symbol var-from-def) :value @@var-from-def})))
   :render-fn '(fn [{:keys [var-name value]}]
                 (v/html
                  (let [choices [:stream :latest]]
                    [:div.flex.justify-between.items-center
                     (into [:div.flex.items-center.font-sans.text-xs.mb-3 [:span.text-slate-500.mr-2 "View-as:"]]
                           (map (fn [choice]
                                  [:button.px-3.py-1.font-medium.hover:bg-indigo-50.rounded-full.hover:text-indigo-600.transition
                                   {:class (if (= value choice) "bg-indigo-100 text-indigo-600" "text-slate-500")
                                    :on-click #(v/clerk-eval `(reset! ~var-name ~choice))}
                                   choice]) choices))
                     [:button.text-xs.rounded-full.px-3.py-1.border-2.font-sans.hover:bg-slate-100.cursor-pointer {:on-click #(v/clerk-eval `(reset-taps!))} "Clear"]])))})

^{::clerk/viewer switch-view}
(defonce !view (atom :stream))

^{::clerk/viewer clerk/hide-result}
(defonce !taps (atom []))

^{::clerk/viewer clerk/hide-result}
(defn reset-taps! []
  (reset! !taps [])
  (clerk/recompute!))

#_(reset-taps!)

^{::clerk/viewer clerk/hide-result}
(defn inst->local-time-str [inst]
  (str (LocalTime/ofInstant inst (ZoneId/systemDefault))))

#_(inst->local-time-str (Instant/now))

^{::clerk/viewer clerk/hide-result}
(def tap-viewer
  {:name :tapped-value
   :render-fn '(fn [{:keys [val tapped-at key]} opts]
                 (v/html (with-meta
                           [:div.border-t.relative.py-3
                            [:span.absolute.rounded-full.px-2.bg-gray-300.font-mono.top-0
                             {:class "left-1/2 -translate-x-1/2 -translate-y-1/2 py-[1px] text-[9px]"} (:nextjournal/value tapped-at)]
                            [:div.overflow-x-auto [v/inspect val]]]
                           {:key (:nextjournal/value key)})))
   :transform-fn (comp clerk/mark-preserve-keys
                       (clerk/update-val #(update % :tapped-at inst->local-time-str)))})

^{::clerk/viewer clerk/hide-result}
(clerk/add-viewers! [tap-viewer])

^{::clerk/viewer clerk/hide-result}
(def taps-viewer
  {:render-fn '#(v/html (into [:div.flex.flex-col.pt-2] (v/inspect-children %2) %1))
   :transform-fn (clerk/update-val (fn [taps]
                                     (mapv (partial clerk/with-viewer :tapped-value) (reverse taps))))})

^{::clerk/viewer (cond-> taps-viewer
                   (= :latest @!view)
                   (update :transform-fn (fn [orig] (comp orig (clerk/update-val (partial take-last 1))))))}
@!taps

^{::clerk/viewer clerk/hide-result}
(defn tapped [x]
  (swap! !taps conj {:val x :tapped-at (java.time.Instant/now) :key (str (gensym))})
  (clerk/recompute!))

#_(tapped (rand-int 1000))

#_(reset! @(find-var 'clojure.core/tapset) #{})

^{::clerk/viewer clerk/hide-result}
(defonce setup
  (add-tap tapped))

#_(remove-tap tapped)

^{::clerk/viewer clerk/hide-result}
(comment
  (dotimes [i 5]
    (tap> (rand-int 1000)))
  (tap> (shuffle (range (+ 20 (rand-int 200)))))
  (tap> (clerk/md "> The purpose of visualization is **insight**, not pictures."))
  (tap> (v/plotly {:data [{:z [[1 2 3]
                               [3 2 1]]
                           :type "surface"}]}))
  (tap> (javax.imageio.ImageIO/read (java.net.URL. "https://images.freeimages.com/images/large-previews/773/koldalen-4-1384902.jpg")))

  (do (require 'rule-30)
      (tap> (clerk/with-viewers (clerk/add-viewers rule-30/viewers) rule-30/rule-30)))

  (tap> (clerk/with-viewers (clerk/add-viewers rule-30/viewers) rule-30/board))

  (tap> (clerk/html [:h1 "Fin. 👋"]))

  ;; ---
  ;; ## TODO

  ;; * [x] Avoid flickering when adding new tap
  ;; * [x] Record & show time of tap
  ;; * [x] Keep expanded state when adding tap
  ;; * [x] Fix latest
  ;; * [ ] Fix lazy loading
  ;; * [ ] Improve performance when large image present in tap stream
  )
