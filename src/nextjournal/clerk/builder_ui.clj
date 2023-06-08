(ns nextjournal.clerk.builder-ui
  {:nextjournal.clerk/visibility {:code :hide :result :hide}
   :nextjournal.clerk/doc-css-class [:pt-0]}
  (:require [nextjournal.clerk.viewer :as viewer]
            [clojure.string :as str]))

(defn status-light [state & [{:keys [size] :or {size 16}}]]
  [:div.flex.items-center.justify-center {:class "w-[24px] h-[24px]"}
   [:div.rounded-full.border.border-greenish-50
    {:class (case state
              (:analyzed :parsed) "bg-greenish border-greenish"
              "bg-greenish-20")
     :style {:width size :height size}}]])

(defn spinner-svg [& [{:keys [size] :or {size 16}}]]
  [:div.flex.items-center.justify-center {:class "w-[24px] h-[24px]"}
   [:svg.animate-spin.text-greenish
    {:xmlns "http://www.w3.org/2000/svg" :fill "none" :viewBox "0 0 24 24"
     :style {:width size :height size}}
    [:circle.opacity-25 {:cx "12" :cy "12" :r "10" :stroke "currentColor" :stroke-width "4"}]
    [:path.opacity-75 {:fill "currentColor" :d "M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"}]]])

(defn checkmark-svg [& [{:keys [size] :or {size 18}}]]
  [:div.flex.justify-center {:class "w-[24px] h-[24px]"}
   [:svg {:xmlns "http://www.w3.org/2000/svg", :fill "none", :viewbox "0 0 24 24", :stroke-width "1.5", :stroke "currentColor", :class "w-6 h-6"}
    [:path {:stroke-linecap "round", :stroke-linejoin "round", :d "M9 12.75L11.25 15 15 9.75M21 12a9 9 0 11-18 0 9 9 0 0118 0z"}]]])

(defn error-svg [& [{:keys [size] :or {size 18}}]]
  [:div.flex.justify-center {:class "w-[24px] h-[24px]"}
   [:svg.text-red-400
    {:xmlns "http://www.w3.org/2000/svg", :viewbox "0 0 20 20", :fill "currentColor"
     :style {:width size :height size}}
    [:path {:fill-rule "evenodd", :d "M8.485 2.495c.673-1.167 2.357-1.167 3.03 0l6.28 10.875c.673 1.167-.17 2.625-1.516 2.625H3.72c-1.347 0-2.189-1.458-1.515-2.625L8.485 2.495zM10 5a.75.75 0 01.75.75v3.5a.75.75 0 01-1.5 0v-3.5A.75.75 0 0110 5zm0 9a1 1 0 100-2 1 1 0 000 2z", :clip-rule "evenodd"}]]])

(def publish-icon-svg
  [:svg {:width "18", :height "18", :viewbox "0 0 18 18", :fill "none", :xmlns "http://www.w3.org/2000/svg"}
   [:path {:d "M9 17C12.7267 17 15.8583 14.4517 16.7473 11.0026M9 17C5.27327 17 2.14171 14.4517 1.25271 11.0026M9 17C11.2091 17 13 13.4183 13 9C13 4.58172 11.2091 1 9 1M9 17C6.79086 17 5 13.4183 5 9C5 4.58172 6.79086 1 9 1M9 1C11.9913 1 14.5991 2.64172 15.9716 5.07329M9 1C6.00872 1 3.40088 2.64172 2.02838 5.07329M15.9716 5.07329C14.102 6.68924 11.6651 7.66667 9 7.66667C6.33486 7.66667 3.89802 6.68924 2.02838 5.07329M15.9716 5.07329C16.6264 6.23327 17 7.573 17 9C17 9.69154 16.9123 10.3626 16.7473 11.0026M16.7473 11.0026C14.4519 12.2753 11.8106 13 9 13C6.18943 13 3.54811 12.2753 1.25271 11.0026M1.25271 11.0026C1.08775 10.3626 1 9.69154 1 9C1 7.573 1.37362 6.23327 2.02838 5.07329", :stroke "currentColor", :stroke-linecap "round", :stroke-linejoin "round"}]])

^{:nextjournal.clerk/visibility {:result :show}}
(viewer/html
 [:<>
  [:style {:type "text/css"}
   ":root {
     --greenish: rgba(146, 189, 154, 1);
     --greenish-60: rgba(146, 189, 154, 0.6);
     --greenish-50: rgba(146, 189, 154, 0.5);
     --greenish-30: rgba(146, 189, 154, 0.3);
     --greenish-20: rgba(146, 189, 154, 0.2);
   }
   html { overflow-y: auto !important; }
   body { background: #000 !important; font-family: 'Inter', sans-serif; color: var(--greenish); }
   .scroll-container { height: auto !important; }
   #clerk-static-app > div { background: #000 !important; height: auto !important; }
   #clerk-static-app .viewer-notebook > :first-child { display: none; }
   .dark-mode-toggle { display: none; }
   a { color: var(--greenish); transition: all 0.125s ease;}
   a:hover { color: white; }
   .viewer-notebook { padding: 0 2rem; }
   @media (min-width: 768px) { .viewer-notebook { margin-left: auto; margin-right: auto; } }
   @media (min-width: 1024px) { .viewer-notebook { padding: 0; max-width: 1024px; } }
   .viewer-result { margin: 0; }
   .viewer-result + .viewer-result { margin: 0; }
   .font-iosevka { font-family: 'Iosevka Web', monospace; }
   .font-inter { font-family: 'Inter', sans-serif; }
   .text-greenish { color: var(--greenish); }
   .text-greenish-60 { color: var(--greenish-60); }
   .bg-greenish { background-color: var(--greenish); }
   .bg-greenish-20 { background-color: var(--greenish-20); }
   .bg-greenish-30 { background-color: var(--greenish-30); }
   .border-greenish { border-color: var(--greenish); }
   .border-greenish-50 { border-color: var(--greenish-30); }
   .separator-top { border-top: 4px solid var(--greenish-50); }
   .separator-bottom { border-bottom: 4px solid var(--greenish-50); }
   .section-heading { border-top: 4px solid var(--greenish-50); }
   .link-hairline { border-bottom: 1px solid var(--greenish-60); }
   .link-hairline:hover { border-color: white; }
   .twitter-card iframe { border: 3px solid var(--greenish-30); border-radius: 15px; overflow: hidden; margin-top: -10px; }
   @keyframes border-pulse {
    0%   { border-color: rgba(146, 189, 154, 1); }
    50%  { border-color: rgba(146, 189, 154, 0.2); }
    100% { border-color: rgba(146, 189, 154, 1); }
   }
   .animate-border-pulse { animation: border-pulse 2s infinite; }"]
  [:link {:rel "preconnect" :href "https://fonts.bunny.net"}]
  [:link {:rel "stylesheet" :href "https://fonts.bunny.net/css?family=inter:400,600"}]
  [:link {:rel "preconnect" :href "https://ntk148v.github.io"}]
  [:link {:rel "stylesheet" :href "https://ntk148v.github.io/iosevkawebfont/latest/iosevka.css"}]])

#_
(defn blocks-view [{:keys [blocks block-counts]}]
  [:div.rounded-b.mx-2.border.border-slate-300.bg-slate-50.shadow
   (into [:div]
         (comp (filter (comp #{:code} :type))
               (map (fn [{:keys [exec-duration exec-state exec-ratio text var]}]
                      [:div.font-mono.px-3.py-1.border-b.border-slate-200.last:border-0.flex.items-center.justify-between
                       {:class ["text-[10px]"
                                (when (= :done exec-state) "bg-green-50")]}
                       [:div.flex.items-center
                        (case exec-state
                          :done (checkmark-svg {:size 14})
                          :executing (spinner-svg {:size 11})
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
                           [:span.text-slate-500 "Queued"])]]])))
         blocks)])

(defn bg-class [state]
  (case state
    :done "bg-greenish-100"
    :errored "bg-red-100"
    "bg-slate-100"))

(defn doc-build-badge [{:as doc :keys [blocks block-counts code-blocks file phase error state duration total-duration]}]
  [:<>
   [:div
    [:div.border-t.border-greenish-50.px-1.py-2.font-iosevka.text-greenish
     #_{:class (bg-class state)}
     [:div.flex.justify-between.items-center
      [:div.flex.items-center.truncate.mr-2
       [:div.mr-2
        (case state
          :executing (spinner-svg)
          :done (checkmark-svg)
          :errored (error-svg)
          (status-light state))]
       [:span.text-sm.mr-1 (case state
                             :executing "Building"
                             :done "Built"
                             :queued "Queued"
                             :errored "Errored"
                             (str "unexpected state `" (pr-str state) "`"))]
       [:div.text-sm.font-medium.leading-none.truncate
        file]]

      (when-let [{:keys [code code-executing]} (not-empty block-counts)]
        [:div.flex-shrink-0.whitespace-no-wrap.flex.items-center
         [:span.text-xs.mr-3
          (when code
            [:<>
             (when code-executing
               [:<> [:span.font-bold code-executing] " of "])
             (str code " code blocks")])]
         [:div.inline-flex.items-center
          [:div.bg-greenish-20
           {:class "h-[4px] w-[50px]"}
           (when duration
             [:div.bg-greenish.border-r.border-greenish
              {:class "h-[6px] -mt-[1px] min-w-[2px]"
               :style {:width (str (int (* 100 (/ duration total-duration))) "%")}}])]
          [:span.font-mono.ml-1
           {:class "w-[40px] text-[10px]"}
           (if duration
             (str (int duration) "ms")
             "•")]]])]]
    (when error
      [:div.overflow-x-auto.rounded-lg
       [:div.relative
        {:nextjournal/value error}]])]
   #_(when (= :executing state)
       (blocks-view doc))
   #_[:div.mx-auto.w-8.border.border-t-0.border-slate-300.bg-slate-50.rounded-b.text-slate-500.flex.justify-center.shadow.hover:bg-slate-100.cursor-pointer
      ]])


#_(doc-build-badge (-> @!build-state :docs (nth 3)))

(def doc-build-badge-viewer
  {:transform-fn (viewer/update-val (comp viewer/html doc-build-badge))})

(defn phase-view [{:keys [phase-name docs error state duration]}]
  [:div.max-w-4xl.mx-auto.px-4.lg:px-0
   [:div.border-t.border-greenish-50.px-1.py-2.font-iosevka.text-greenish
    #_{:class (bg-class state)}
    [:div.flex.justify-between.items-center
     [:div.flex.items-center
      [:div.mr-2
       (case state
         :executing (spinner-svg)
         :done (checkmark-svg)
         :errored (error-svg)
         (status-light state))]
      (when (not= state :done)
        [:span.text-sm.mr-1 (case state
                              :executing "Building"
                              :queued "Queued"
                              :errored "Errored")])
      [:div.text-sm.font-medium.leading-none
       phase-name]]

     [:div.flex.items-center
      (when docs
        [:div.text-xs.mr-3 (count docs) " notebooks"])
      (when duration
        [:span.font-mono.ml-1
         {:class "w-[40px] text-[10px]"}
         (int duration) "ms"])]]]
   (when error
     [:div.overflow-x-auto.rounded-lg
      [:div.relative
       {:nextjournal/value error}]])])


(def phase-viewer
  {:transform-fn (viewer/update-val (comp viewer/html phase-view))})

(def docs-viewer
  {:render-fn '(fn [state opts]
                 (into [:div.flex.flex-col.border-greenish-50.mt-8.max-w-4xl.mx-auto.px-4.lg:px-0
                        {:class (when (seq state) "border-t-[3px]")}] (nextjournal.clerk.render/inspect-children opts) state))
   :transform-fn (viewer/update-val (fn [docs]
                                      (mapv #(viewer/with-viewer doc-build-badge-viewer %) docs)))})


^:nextjournal.clerk/no-cache
(defn process-docs [docs]
  (mapv (fn [{:as doc :keys [blocks]}]
          (-> doc
              (select-keys [:file :title :blocks])
              (update :blocks (fn [blocks] (mapv #(select-keys % [:text :type :var]) blocks)))
              (assoc :state :queued :block-counts (frequencies (map :type blocks)))))
        docs))

(defn compute-total-duration [docs]
  (let [total-duration (apply + (keep :duration docs))]
    (mapv #(assoc % :total-duration total-duration) docs)))

(def initial-build-state
  {:parsing {:phase-name "Parsing" :state :executing}
   :analyzing {:phase-name "Analyzing" :state :queued}})

(defn next-build-state [build-state {:as event :keys [stage state error duration doc idx]}]
  (case stage
    :init (if error
            (assoc build-state :error error)
            (assoc build-state :docs (process-docs state)))
    (:parsed :analyzed) (-> build-state
                            (update :docs (fn [old-docs] (if error old-docs (process-docs state))))
                            (update ({:parsed :parsing
                                      :analyzed :analyzing} stage)
                                    merge
                                    {:phase-name (-> stage name str/capitalize) :duration duration}
                                    (if error
                                      {:state :errored :error error}
                                      {:state :done})))
    :building (update-in build-state [:docs idx] merge {:state :executing})
    :built (-> build-state
               (update-in [:docs idx] merge {:duration duration} (if error {:state :errored :error error} {:state :done}))
               (update :docs compute-total-duration))
    :finished (merge build-state (select-keys state [:index-html :build-href]))
    build-state))

(defonce !build-state (atom initial-build-state))
(defonce !build-events (atom []))

(defn reset-build-state! []
  (reset! !build-state initial-build-state)
  (reset! !build-events []))


(defn add-build-event! [event]
  (swap! !build-state next-build-state event)
  (swap! !build-events conj event))

(comment
  (nextjournal.clerk.builder/build-static-app! {:paths (take 10 nextjournal.clerk.builder/clerk-docs)
                                                :browse? false})

  (do
    (reset! !build-state (reduce next-build-state initial-build-state (take 100 @!build-events)))
    (nextjournal.clerk/recompute!)))

{:nextjournal.clerk/visibility {:result :show}}

^{:nextjournal.clerk/width :full}
(viewer/html
 [:div.border-greenish-50.font-iosevka.text-greenish.flex.items-center.justify-between.pl-1.not-prose.mt-8.max-w-4xl.mx-auto
  (if-let [link (:build-href @!build-state)]
    [:<>
     [:div.flex.items-center.px-4.lg:px-0
      (checkmark-svg)
      [:div.text-lg.ml-2.mb-0.font-medium "Your notebooks have been built."]]
     [:a.font-medium.rounded-full.text-sm.px-3.py-1.bg-greenish-20.flex.items-center.border-2.border-greenish.animate-border-pulse.hover:border-white.hover:animate-none
      {:href link}
      [:div publish-icon-svg]
      [:span.ml-2 "Open"]]]
    [:div.flex.items-center.px-4.lg:px-0
     (spinner-svg)
     [:div.text-lg.ml-2.mb-0 "Building notebooks…"]])])

^{:nextjournal.clerk/viewer phase-viewer :nextjournal.clerk/width :full}
(merge (:parsing @!build-state) (select-keys @!build-state [:docs]))

^{:nextjournal.clerk/viewer phase-viewer :nextjournal.clerk/width :full}
(merge (:analyzing @!build-state) (select-keys @!build-state [:docs]))

^{:nextjournal.clerk/viewer docs-viewer :nextjournal.clerk/width :full}
(:docs @!build-state)
