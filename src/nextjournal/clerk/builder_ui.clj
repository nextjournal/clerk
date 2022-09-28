(ns nextjournal.clerk.builder-ui
  {:nextjournal.clerk/visibility {:code :hide :result :hide}}
  (:require [nextjournal.clerk.viewer :as viewer]
            [clojure.string :as str]))

(defn status-light [state & [{:keys [size] :or {size 14}}]]
  [:div.rounded-full.border
   {:class (case state
             (:analyzed :parsed) "bg-orange-400 border-orange-700"
             "bg-slate-400 border-slate-600")
    :style {:box-shadow "inset 0 1px 3px rgba(255,255,255,.6)"
            :width size :height size}}])

(defn spinner [& [{:keys [size] :or {size 14}}]]
  [:svg.animate-spin.text-green-600
   {:xmlns "http://www.w3.org/2000/svg" :fill "none" :viewBox "0 0 24 24"
    :style {:width size :height size}}
   [:circle.opacity-25 {:cx "12" :cy "12" :r "10" :stroke "currentColor" :stroke-width "4"}]
   [:path.opacity-75 {:fill "currentColor" :d "M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"}]])

(defn checkmark [& [{:keys [size] :or {size 18}}]]
  [:svg.text-green-600
   {:xmlns "http://www.w3.org/2000/svg" :viewBox "0 0 20 20" :fill "currentColor"
    :class "-ml-[1px]"
    :style {:width size :height size}}
   [:path {:fill-rule "evenodd" :d "M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" :clip-rule "evenodd"}]])

(defn badge [{:keys [blocks code-blocks file state]}]
  [:div.p-1
   [:div.rounded-md.border.border-slate-300.px-4.py-3.font-sans.shadow
    {:class (if (= state :done) "bg-green-100" "bg-slate-100")}
    [:div.flex.justify-between.items-center
     [:div.flex.items-center
      [:div.mr-2
       (case state
         :executing (spinner)
         :done (checkmark)
         (status-light state))]
      [:span.text-sm.mr-1 (case state
                            :analyzed "Analyzed"
                            :executing "Building"
                            :done "Built"
                            :parsed "Parsed"
                            "Waiting for ")]
      [:div.text-sm.font-medium.leading-none
       file]]
     (when-let [{:as block-counts :keys [code markdown code-executing]} (and blocks (frequencies (mapv :type blocks)))]
       [:<>
        [:div.text-sm
         (when code
           [:<>
            (when code-executing
              [:<> [:span.font-bold code-executing] " of "])
            (str code " code")])
         (when markdown (str (when code " & ") markdown " markdown"))
         " blocks"]
        #_[:div.rounded-b.mx-2.border.border-slate-300.bg-slate-50.shadow
           (into [:div]
                 (map (fn [{:keys [exec-duration exec-state exec-ratio text var]}]
                        [:div.font-mono.px-3.py-1.border-b.border-slate-200.last:border-0.flex.items-center.justify-between
                         {:class (str "text-[10px] "
                                      (when (= :done exec-state) "bg-green-50"))}
                         [:div.flex.items-center
                          (case exec-state
                            :done (checkmark {:size 14})
                            :executing (spinner {:size 11})
                            (status-light exec-state {:size 11}))
                          [:div.ml-2
                           (if var
                             (name var)
                             [:span.text-slate-400
                              (let [max-len 40
                                    count (count text)]
                                (if (<= count max-len)
                                  text
                                  (str (subs text 0 max-len) "…")))])]]
                         [:div.flex.items-center
                          (let [max-width 150]
                            [:div.rounded-full.mr-3
                             {:class (str "h-[4px] "
                                          (if (contains? #{:done :executing} exec-state)
                                            "bg-green-600 "
                                            "bg-slate-200 "))
                              :style {:min-width 1
                                      :width (* max-width (or exec-ratio (/ 1 (:code block-counts))))}}])
                          [:div.text-left
                           {:class "w-[50px]"}
                           (if exec-duration
                             [:span
                              (format "%.3f" (/ exec-duration 1000.0))
                              [:span.text-slate-500 {:class "ml-[1px]"} "s"]]
                             [:span.text-slate-500 "Queued"])]]])
                      code-blocks))]])]]
   #_[:div.mx-auto.w-8.border.border-t-0.border-slate-300.bg-slate-50.rounded-b.text-slate-500.flex.justify-center.shadow.hover:bg-slate-100.cursor-pointer
      [:svg.h-3.w-3
       {:xmlns "http://www.w3.org/2000/svg" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor" :stroke-width "2"}
       [:path {:stroke-linecap "round" :stroke-linejoin "round" :d "M19 9l-7 7-7-7"}]]]])

(def badge-viewer
  {:transform-fn (viewer/update-val (comp viewer/html badge))})

(defonce !build-state
  (atom []))

#_(reset! !state [])

(def build-state-viewer
  {:render-fn '(fn [state opts]
                 (v/html (into [:div.flex.flex-col.pt-2] (v/inspect-children opts) state)))
   :transform-fn (viewer/update-val (fn [{:keys [stage state]}]
                                      (mapv (fn [doc-state]
                                              (viewer/with-viewer badge-viewer doc-state))
                                            state)))})

;; # 👷 Clerk Builder 🔨

^{:nextjournal.clerk/visibility {:result :show}
  :nextjournal.clerk/viewer build-state-viewer}
@!build-state


(comment
  (require '[nextjournal.clerk.builder :as b])

  (do
    (reset! !build-state {:stage :init :state (mapv #(hash-map :file %) (take 3 (b/expand-paths `b/clerk-docs)))})
    (nextjournal.clerk/recompute!))

  )
