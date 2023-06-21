;; # 🚰 Tap Inspector
(ns nextjournal.clerk.tap
  {:nextjournal.clerk/visibility {:code :hide :result :hide}}
  (:require [nextjournal.clerk.viewer :as v])
  (:import (java.time Instant LocalTime ZoneId)))

(defn inst->local-time-str [inst] (str (LocalTime/ofInstant inst (ZoneId/systemDefault))))
(defn record-tap [x]
  {::val x ::tapped-at (Instant/now) ::key (str (gensym))})

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
               [:button.text-xs.rounded-full.px-3.py-1.border-2.font-sans.hover:bg-slate-100.cursor-pointer
                {:on-click #(nextjournal.clerk.render/clerk-eval `(reset-taps!))} "Clear"]]))))

^{:nextjournal.clerk/sync true :nextjournal.clerk/viewer switch-view :nextjournal.clerk/visibility {:result :show}}
(defonce !view (atom :stream))

(defonce !taps (atom ()))

(defn reset-taps! []
  (reset! !taps ())
  ((resolve 'nextjournal.clerk/recompute!)))

(defn tapped [x]
  (swap! !taps conj (record-tap x))
  ((resolve 'nextjournal.clerk/recompute!)))

(defonce tap-setup
  (add-tap (fn [x] ((resolve `tapped) x))))

(def tap-viewer
  {:pred (v/get-safe ::val)
   :render-fn '(fn [{::keys [val tapped-at]} opts]
                 [:div.w-full
                  [:div.font-sans.bg-slate-50.py-1.text-slate-600.tracking-wide.border-t.border-b
                   {:class "px-[8px] text-[11px]"} (:nextjournal/value tapped-at)]
                  [:div.overflow-x-auto.py-2
                   {:class "px-[8px]"}
                   [nextjournal.clerk.render/inspect-presented val]]])
   :transform-fn (fn [{:as wrapped-value :nextjournal/keys [value]}]
                   (-> wrapped-value
                       v/mark-preserve-keys
                       (merge (v/->opts (v/ensure-wrapped (::val value)))) ;; preserve opts like ::clerk/width and ::clerk/css-class
                       (assoc-in [:nextjournal/render-opts :id] (::key value)) ;; assign custom react key
                       (update-in [:nextjournal/value ::tapped-at] inst->local-time-str)))})

^{:nextjournal.clerk/visibility {:result :show}
  :nextjournal.clerk/viewers (v/add-viewers [tap-viewer])}
(v/fragment (cond->> @!taps
              (= :latest @!view) (take 1)))

(comment
  (last @!taps)
  (dotimes [_i 5]
    (tap> (rand-int 1000)))
  (tap> (shuffle (range (+ 20 (rand-int 200)))))
  (tap> (v/md "> The purpose of visualization is **insight**, not pictures."))
  (tap> (v/plotly {:data [{:z [[1 2 3] [3 2 1]] :type "surface"}]}))
  (tap> (v/html  {:nextjournal.clerk/width :full} [:h1.w-full.border-2.border-amber-500.bg-amber-500.h-10]))
  (tap> (v/table {:nextjournal.clerk/width :full} [[1 2] [3 4]]))
  (tap> (v/plotly {:nextjournal.clerk/width :full} {:data [{:y [3 1 2]}]}))
  (tap> (v/image "trees.png"))
  (do (require 'rule-30)
      (tap> (v/with-viewers (v/add-viewers rule-30/viewers) rule-30/rule-30)))
  (tap> (v/with-viewers (v/add-viewers rule-30/viewers) rule-30/board))
  (tap> (v/html [:h1 "Fin. 👋"]))
  (tap> (reduce (fn [acc _] (vector acc)) :fin (range 200)))
  (reset-taps!))
