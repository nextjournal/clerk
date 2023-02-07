;; # 🚰 Tap Inspector
(ns nextjournal.clerk.tap
  {:nextjournal.clerk/visibility {:code :hide :result :hide}}
  (:require [clojure.core :as core]
            [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as v])
  (:import (java.time LocalTime ZoneId)))

(def switch-view
  (assoc v/viewer-eval-viewer
         :render-fn
         '(fn [!view]
            (let [choices [:stream :latest]]
              [:div.flex.justify-between.items-center
               (into [:div.flex.items-center.font-sans.text-xs.mb-3 [:span.text-slate-500.mr-2 "View-as:"]]
                     (map (fn [choice]
                            [:button.px-3.py-1.font-medium.hover:bg-indigo-50.rounded-full.hover:text-indigo-600.transition
                             {:class (if (= @!view choice) "bg-indigo-100 text-indigo-600" "text-slate-500")
                              :on-click #(reset! !view choice)}
                             choice]) choices))
               [:button.text-xs.rounded-full.px-3.py-1.border-2.font-sans.hover:bg-slate-100.cursor-pointer {:on-click #(v/clerk-eval `(reset-taps!))} "Clear"]]))))

^{::clerk/sync true ::clerk/viewer switch-view ::clerk/visibility {:result :show}}
(defonce !view (atom :stream))

(defonce !taps (atom []))

(defn reset-taps! []
  (reset! !taps [])
  (clerk/recompute!))

#_(reset-taps!)

(defn inst->local-time-str [inst]
  (str (LocalTime/ofInstant inst (ZoneId/systemDefault))))

#_(inst->local-time-str (Instant/now))

(def tap-viewer
  {:name `tap-viewer
   :render-fn '(fn [{:keys [val tapped-at key]} opts]
                 (with-meta
                   [:div.border-t.relative.py-3
                    [:span.absolute.rounded-full.px-2.bg-gray-300.font-mono.top-0
                     {:class "left-1/2 -translate-x-1/2 -translate-y-1/2 py-[1px] text-[9px]"} (:nextjournal/value tapped-at)]
                    [:div.overflow-x-auto [v/inspect-presented val]]]
                   {:key (:nextjournal/value key)}))
   :transform-fn (comp clerk/mark-preserve-keys
                       (clerk/update-val #(update % :tapped-at inst->local-time-str)))})

(clerk/add-viewers! [tap-viewer])

(def taps-viewer
  {:render-fn '#(into [:div.flex.flex-col.pt-2] (v/inspect-children %2) %1)
   :transform-fn (clerk/update-val (fn [taps]
                                     (mapv (partial clerk/with-viewer `tap-viewer) (reverse taps))))})

^{::clerk/visibility {:result :show}
  ::clerk/viewer (cond-> taps-viewer
                   (= :latest @!view)
                   (update :transform-fn (fn [orig] (comp orig (clerk/update-val (partial take-last 1))))))}
@!taps

(defn tapped [x]
  (swap! !taps conj {:val x :tapped-at (java.time.Instant/now) :key (str (gensym))})
  (clerk/recompute!))

#_(tapped (rand-int 1000))

#_(reset! @(find-var 'clojure.core/tapset) #{})

(defonce setup
  (add-tap tapped))

#_ (remove-tap tapped)


(comment
  (last @!taps)

  (dotimes [_i 5]
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

  (reset-taps!)
  )
