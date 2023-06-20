(ns nextjournal.clerk.render.navbar
  (:require ["framer-motion" :as framer-motion :refer [m AnimatePresence]]
            [nextjournal.clerk.render.localstorage :as localstorage]
            [applied-science.js-interop :as j]
            [clojure.string :as str]
            [reagent.core :as r]))

(defn stop-event! [event]
  (.preventDefault event)
  (.stopPropagation event))

(def !scroll-animation (atom false))

(defn scroll-to-anchor!
  "Uses framer-motion to animate scrolling to a section.
  `offset` here is just a visual offset. It looks way nicer to stop
  just before a section instead of having it glued to the top of
  the viewport."
  [anchor]
  (let [scroll-el (js/document.querySelector "html")
        scroll-top (.-scrollTop scroll-el)
        offset 40]
    (when-let [anim @!scroll-animation]
      (.stop anim))
    (reset! !scroll-animation
            (.animate framer-motion
                      scroll-top
                      (+ scroll-top (.. (js/document.getElementById (subs anchor 1)) getBoundingClientRect -top))
                      (j/lit {:onUpdate #(j/assoc! scroll-el :scrollTop (- % offset))
                              :onComplete #(reset! !scroll-animation nil)
                              :type :spring
                              :duration 0.4
                              :bounce 0.15})))))

(defn navigate-or-scroll! [event {:as item :keys [path]} {:keys [set-hash?]}]
  (let [[path-name search] (.split path "?")
        current-path-name (.-pathname js/location)
        anchor-only? (str/starts-with? path-name "#")
        [_ hash] (some-> search (.split "#"))]
    (when (or (and search hash (= path-name current-path-name)) anchor-only?)
      (let [anchor (if anchor-only? path-name (str "#" hash))]
        (.preventDefault event)
        (when set-hash?
          (.pushState js/history #js {} "" anchor))
        (scroll-to-anchor! anchor)))))

(defn render-items [items {:as render-opts :keys [!expanded-at expandable-toc? mobile-toc?]}]
  (into
   [:div]
   (map-indexed
    (fn [i {:as item :keys [emoji path title items]}]
      (let [label (or title (str/capitalize (last (str/split path #"/"))))
            expanded? (get @!expanded-at path)]
        [:div.text-base.leading-normal.dark:text-white
         {:class "md:text-[14px]"}
         (if (seq items)
           [:div.flex.relative.hover:bg-slate-200.dark:hover:bg-slate-900.rounded.group.transition
            {:class (str "ml-[8px] mr-[4px] gap-[2px] "
                         (if expandable-toc? "pl-[2px] pr-[6px]" "px-[6px]"))}
            (when expandable-toc?
              [:div.flex.items-center.justify-center.relative.flex-shrink-0.border.border-transparent.hover:border-indigo-700.hover:bg-indigo-500.dark:hover:bg-indigo-700.hover:shadow.text-slate-600.hover:text-white.dark:text-slate-400.dark:hover:text-white.rounded.cursor-pointer.active:scale-95
               {:class "w-[18px] h-[18px] top-[5px]"
                :on-click (fn [event]
                            (stop-event! event)
                            (swap! !expanded-at update path not))}
               [:svg.w-3.h-3.transition
                {:xmlns "http://www.w3.org/2000/svg" :fill "none" :viewBox "0 0 24 24" :stroke-width "1.5" :stroke "currentColor"
                 :class (if expanded? "rotate-90" "rotate-0")}
                [:path {:stroke-linecap "round" :stroke-linejoin "round" :d "M8.25 4.5l7.5 7.5-7.5 7.5"}]]])
            [:a.py-1.flex.flex-auto.gap-1.group-hover:text-indigo-700.dark:group-hover:text-white.hover:underline.decoration-indigo-300.dark:decoration-slate-400.underline-offset-2
             {:href path
              :class (when expanded? "font-medium")
              :on-click (fn [event]
                          (navigate-or-scroll! event item render-opts)
                          (when mobile-toc?
                            (swap! !expanded-at assoc :toc-open? false)))}
             (when emoji
               [:span.flex-shrink-0 emoji])
             [:span (if emoji (subs label (count emoji)) label)]]
            (when expanded?
              [:span.absolute.bottom-0.border-l.border-slate-300.dark:border-slate-600
               {:class "top-[25px] left-[10px]"}])]
           [:a.flex.flex-auto.gap-1.py-1.rounded.hover:bg-slate-200.dark:hover:bg-slate-900.hover:text-indigo-700.dark:hover:text-white.hover:underline.decoration-indigo-300.dark:decoration-slate-400.underline-offset-2.transition
            {:class "px-[6px] ml-[8px] mr-[4px]"
             :href path
             :on-click (fn [event]
                         (navigate-or-scroll! event item render-opts)
                         (when mobile-toc?
                           (swap! !expanded-at assoc :toc-open? false)))}
            (when emoji
              [:span.flex-shrink-0 emoji])
            [:span (if emoji (subs label (count emoji)) label)]])
         (when (and (seq items) (or (not expandable-toc?) (and expandable-toc? expanded?)))
           [:div.relative
            {:class (str (if expandable-toc? "ml-[16px] " "ml-[19px] ")
                         (when expanded? "mb-2"))}
            (when expanded?
              [:span.absolute.top-0.border-l.border-slate-300.dark:border-slate-600
               {:class "left-[2px] bottom-[8px]"}])
            [render-items items render-opts]])]))
    items)))

(def local-storage-key "clerk-navbar")

(defn mobile? []
  (and (exists? js/innerWidth) (< js/innerWidth 640)))

(def spring {:type :spring :duration 0.35 :bounce 0.1})

(defn mobile-backdrop [{:keys [!expanded-at]}]
  [:> (.-div m)
   {:key "mobile-toc-backdrop"
    :class "fixed z-10 bg-gray-500 bg-opacity-75 left-0 top-0 bottom-0 right-0"
    :initial {:opacity 0}
    :animate {:opacity 1}
    :exit {:opacity 0}
    :on-click #(swap! !expanded-at assoc :toc-open? false)
    :transition spring}])

(defn close-button [{:keys [!expanded-at mobile-toc?]}]
  [:div.toc-toggle.rounded.hover:bg-slate-200.active:bg-slate-300.dark:hover:bg-slate-900.active:dark:bg-slate-950.p-1.text-slate-500.hover:text-slate-600.dark:hover:text-white.absolute.right-2.cursor-pointer.z-10
   {:class "top-[11px] -mt-1 -mr-1"
    :on-click #(swap! !expanded-at update :toc-open? not)}
   (if mobile-toc?
     [:svg.h-5.w-5 {:xmlns "http://www.w3.org/2000/svg" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor" :stroke-width "2"}
      [:path {:stroke-linecap "round" :stroke-linejoin "round" :d "M6 18L18 6M6 6l12 12"}]]
     [:svg.w-4.w-4 {:xmlns "http://www.w3.org/2000/svg" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor" :stroke-width "2"}
      [:path {:stroke-linecap "round" :stroke-linejoin "round" :d "M15 19l-7-7 7-7"}]])])

(defn open-button [{:keys [!expanded-at]}]
  (r/with-let [ref-fn #(when %
                         (add-watch !expanded-at ::toc-open-watch
                                    (fn [_ _ old {:keys [toc-open?]}]
                                      (when (not= (:toc-open? old) toc-open?)
                                        (localstorage/set-item! local-storage-key toc-open?)))))]
    [:div.toc-toggle
     {:ref ref-fn
      :class "z-10 fixed right-2 top-2 md:right-auto md:left-3 md:top-[7px] text-slate-400 font-sans text-xs hover:underline cursor-pointer flex items-center bg-white dark:bg-gray-900 py-1 px-3 md:p-0 rounded-full md:rounded-none border md:border-0 border-slate-200 dark:border-gray-500 shadow md:shadow-none dark:text-slate-400 dark:hover:text-white"
      :on-click #(swap! !expanded-at assoc :toc-open? true)}
     [:svg {:xmlns "http://www.w3.org/2000/svg" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor" :width 20 :height 20}
      [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M4 6h16M4 12h16M4 18h16"}]]
     [:span.uppercase.tracking-wider.ml-1.font-bold
      {:class "text-[12px]"} "ToC"]]))

(def width 220)
(def mobile-width 300)

(defn toc-panel [toc {:as render-opts :keys [!expanded-at mobile-toc?]}]
  [:> (.-div m)
   (let [inset-or-x (if mobile-toc? :x :margin-left)
         w (if mobile-toc? mobile-width width)]
     {:key "toc-panel"
      :style {:width w}
      :class (str "fixed h-screen z-10 flex-shrink-0 bg-slate-100 dark:bg-gray-800 font-sans border-r dark:border-slate-900 "
                  (when mobile-toc? "shadow-xl"))
      :initial {inset-or-x (* w -1)}
      :animate {inset-or-x 0}
      :exit {inset-or-x (* w -1)}
      :transition spring})
   [close-button render-opts]
   [:div.absolute.left-0.top-0.w-full.h-full.overflow-x-hidden.overflow-y-auto.py-3
    [:div.px-3.mb-1.mt-1.md:mt-0.text-xs.uppercase.tracking-wider.text-slate-500.dark:text-slate-400.font-medium.px-3.mb-1.leading-none
     {:class "md:text-[12px]"}
     "TOC"]
    [render-items toc render-opts]]])

(defn view [toc {:as render-opts :keys [!expanded-at]}]
  (r/with-let [!mobile-toc? (r/atom (mobile?))
               handle-resize #(reset! !mobile-toc? (mobile?))
               ref-fn #(if %
                         (js/addEventListener "resize" handle-resize)
                         (js/removeEventListener "resize" handle-resize))]
    (let [{:keys [toc-open?]} @!expanded-at
          mobile-toc? @!mobile-toc?]
      [:div {:ref ref-fn}
       [open-button render-opts]
       (when (and mobile-toc? toc-open?)
         [mobile-backdrop render-opts])
       [:> AnimatePresence
        {:initial false}
        (when toc-open?
          [toc-panel toc (assoc render-opts :mobile-toc? mobile-toc?)])]])))
