(ns nextjournal.clerk.render.log
  (:require [nextjournal.clerk.render.localstorage :as localstorage]
            [nextjournal.clerk.viewer :as viewer]
            [reagent.core :as reagent]))

(defonce !log-visible? (reagent/atom (localstorage/get-item "log-visible")))

(defonce !history (reagent/atom []))

(defn show []
  (reset! !log-visible? true)
  (localstorage/set-item! "log-visible" true))

(defn log [event-name & pairs]
  (js/console.log "log" event-name pairs)
  (swap! !history conj {:event-name event-name
                        :timestamp (js/Date.)
                        :pairs pairs}))

(defn panel []
  [:div.fixed.left-0.right-0.bottom-0.z-30.border-t.border-slate-300.flex.flex-col.bg-white
   {:class "h-[420px]"}
   [:div.bg-slate-100.p-1.border-b.border-slate-300.flex.items-center
    [:div.text-slate-700.hover:text-slate-900.cursor-pointer
     {:on-click (fn []
                  (reset! !log-visible? false)
                  (localstorage/remove-item! "log-visible"))}
     [:svg {:xmlns "http://www.w3.org/2000/svg", :fill "none", :viewBox "0 0 24 24", :stroke-width "1.5", :stroke "currentColor", :class "w-[16px] h-[16px]"}
      [:path {:stroke-linecap "round", :stroke-linejoin "round", :d "M6 18L18 6M6 6l12 12"}]]]]
   [:div.bg-white.flex-auto.overflow-y-auto
    (into [:div]
          (map (fn [{:keys [event-name timestamp pairs]}]
                 [:div.flex.gap-3.p-1.border-b
                  [:div
                   [:div.text-white.rounded-sm.font-mono.flex-shrink-0.whitespace-nowrap.leading-none
                    {:class "text-[10px] px-[5px] py-[3px] bg-blue-600"}
                    event-name]]
                  (into [:div.flex-auto.text-xs.font-mono]
                        (map (fn [[a b]]
                               [:div.flex.gap-3
                                [:div.whitespace-nowrap a]
                                [:div b]]))
                        (partition 2 pairs))
                  [:div
                   [:div.font-mono.flex-shrink-0.whitespace-nowrap.pr-1
                    {:class "text-[10px]"}
                    (.toLocaleTimeString timestamp "en-US")]]]))
          @!history)]
   [:div.bg-slate-100.p-1.border-t.border-slate-300.flex.items-center
    [:textarea.w-full.h-full.appearance-none.font-mono.text-xs.rounded-md.border.border-slate-300.p-2]]])
