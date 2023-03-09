;; # 🚰 Tap Inspector via Fragments
;; Using fragments to implement a tap viewer
^{:nextjournal.clerk/visibility {:code :hide}}
(ns fragments
  {:nextjournal.clerk/visibility {:code :hide :result :hide}
   :nextjournal.clerk/no-cache true}
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as v])
  (:import (java.time Instant LocalTime ZoneId)))

(defn inst->local-time-str [inst] (str (LocalTime/ofInstant inst (ZoneId/systemDefault))))

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

^{::clerk/sync true ::clerk/viewer switch-view ::clerk/visibility {:result :show}}
(defonce !view (atom :stream))

(defonce !taps (atom [(clerk/html {::clerk/width :wide} [:div.w-full.border-2.border-amber-500])
                      (clerk/plotly {::clerk/width :full} {:data [{:y [3 1 2]}]})]))

(defn reset-taps! []
  (reset! !taps [])
  (clerk/recompute!))

(defn tapped [x]
  (swap! !taps conj {:val x :tapped-at (Instant/now) :key (str (gensym))})
  (clerk/recompute!))

(defonce tap-setup (add-tap (fn [x] ((resolve `tapped) x))))

(def tap-viewer
  {:transform-fn
   (comp v/mark-presented
         #(merge % (-> % :nextjournal/value :val v/->opts))
         (clerk/update-val update :val v/present)
         (clerk/update-val update :tapped-at inst->local-time-str)
         #(assoc % :nextjournal/viewer
                 {:render-fn '(fn [{:keys [val key tapped-at]} opts]
                                (js/console.log :key key)
                                (with-meta
                                  [:div.border-t.relative.py-3.mt-5
                                   [:span.absolute.rounded-full.px-2.bg-gray-300.font-mono.top-0
                                    {:class "left-1/2 -translate-x-1/2 -translate-y-1/2 py-[1px] text-[9px]"} tapped-at]
                                   [:div.overflow-x-auto [nextjournal.clerk.render/inspect-presented val]]]
                                  {:key key}))}))})

^{::clerk/visibility {:result :show}}
(clerk/fragment (map (partial clerk/with-viewer tap-viewer)
                     (cond->> (reverse @!taps) (= :latest @!view) (take 1))))

(comment
  (doseq [t @@#'clojure.core/tapset] (remove-tap t))
  (tap> 1)
  (tap> (clerk/html  {::clerk/width :full} [:h1.w-full.border-2.border-amber-500.bg-amber-500.h-10]))
  (tap> (clerk/table {::clerk/width :full} [[1 2] [3 4]]))
  (tap> (clerk/plotly {::clerk/width :full} {:data [{:y [3 1 2]}]}))
  (tap> (clerk/html {::clerk/width :full} [:h1 "Fin. 👋"]))
  (reset-taps!))
