(ns nextjournal.clerk.render.navbar
  (:require ["framer-motion" :as framer-motion :refer [m AnimatePresence]]
            [nextjournal.clerk.render.localstorage :as localstorage]
            [applied-science.js-interop :as j]
            [clojure.string :as str]
            [reagent.core :as r]))

(defn stop-event! [event]
  (.preventDefault event)
  (.stopPropagation event))

(defn scroll-to-anchor!
  "Uses framer-motion to animate scrolling to a section.
  `offset` here is just a visual offset. It looks way nicer to stop
  just before a section instead of having it glued to the top of
  the viewport."
  [!state anchor]
  (let [{:keys [mobile? scroll-animation scroll-el set-hash? visible?]} @!state
        scroll-top (.-scrollTop scroll-el)
        offset 40]
    (when scroll-animation
      (.stop scroll-animation))
    (when scroll-el
      (swap! !state assoc
             :scroll-animation (.animate framer-motion
                                         scroll-top
                                         (+ scroll-top (.. (js/document.getElementById (subs anchor 1)) getBoundingClientRect -top))
                                         (j/lit {:onUpdate #(j/assoc! scroll-el :scrollTop (- % offset))
                                                 :onComplete #(when set-hash? (.pushState js/history #js {} "" anchor))
                                                 :type :spring
                                                 :duration 0.4
                                                 :bounce 0.15}))
             :visible? (if mobile? false visible?)))))

(defn theme-class [theme key]
  (-> {:project "py-3"
       :toc "py-3"
       :heading "mt-1 md:mt-0 text-xs md:text-[12px] uppercase tracking-wider text-slate-500 dark:text-slate-400 font-medium px-3 mb-1 leading-none"
       :back "text-xs md:text-[12px] leading-normal text-slate-500 dark:text-slate-400 md:hover:bg-slate-200 md:dark:hover:bg-slate-700 font-normal px-3 py-1"
       :expandable "text-base md:text-[14px] leading-normal md:hover:bg-slate-200 md:dark:hover:bg-slate-700 dark:text-white px-3 py-2 md:py-1"
       :triangle "text-slate-500 dark:text-slate-400"
       :item "text-base md:text-[14px] md:hover:bg-slate-200 md:dark:hover:bg-slate-700 dark:text-white px-3 py-2 md:py-1 leading-normal"
       :icon "text-slate-500 dark:text-slate-400"
       :slide-over "font-sans bg-white border-r"
       :slide-over-unpinned "shadow-xl"
       :toggle "text-slate-500 absolute right-2 top-[11px] cursor-pointer z-10"}
      (merge theme)
      (get key)))

(defn toc-items [!state items & [options]]
  (into [:div]
        (map (fn [{:keys [path emoji title items]}]
               [:<>
                [:a.flex.flex-auto.gap-1.py-1.rounded.hover:bg-slate-200.dark:hover:bg-slate-900.hover:text-indigo-700.dark:text-white.dark:hover:text-white.hover:underline.decoration-indigo-300.dark:decoration-slate-400.underline-offset-2.transition
                 (cond-> {:href path
                          :class "px-[6px] ml-[8px] mr-[4px] md:text-[14px]"}
                   (str/starts-with? path "#")
                   (assoc :on-click (fn [event]
                                      (stop-event! event)
                                      (scroll-to-anchor! !state path))))
                 (when emoji
                   [:span.flex-shrink-0 emoji])
                 [:span (if emoji (subs title (count emoji)) title)]]
                (when (seq items)
                  [:div.ml-3
                   [toc-items !state items]])]))
        items))

(defn navigate-or-scroll! [!state path event]
  (let [[path-name search] (.split path "?")
        current-path-name (.-pathname js/location)
        [_ hash] (some-> search (.split "#"))]
    (when (and search hash (= path-name current-path-name))
      (.preventDefault event)
      (scroll-to-anchor! !state (str "#" hash)))))

(defn navbar-items [!state items update-at]
  (let [{:keys [mobile?]} @!state]
    (into
     [:div]
     (map-indexed
      (fn [i {:keys [emoji path title expanded? items]}]
        (let [label (or title (str/capitalize (last (str/split path #"/"))))]
          [:div.text-base.leading-normal.dark:text-white
           {:class "md:text-[14px]"}
           (if (seq items)
             [:div.flex.relative.hover:bg-slate-200.dark:hover:bg-slate-900.rounded.group.transition
              {:class "ml-[8px] mr-[4px] pl-[2px] pr-[6px] gap-[2px]"}
              [:div.flex.items-center.justify-center.relative.flex-shrink-0.border.border-transparent.hover:border-indigo-700.hover:bg-indigo-500.dark:hover:bg-indigo-700.hover:shadow.text-slate-600.hover:text-white.dark:text-slate-400.dark:hover:text-white.rounded.cursor-pointer.active:scale-95
               {:class "w-[18px] h-[18px] top-[5px]"
                :on-click (fn [event]
                            (stop-event! event)
                            (swap! !state assoc-in (vec (conj update-at i :expanded?)) (not expanded?)))}
               [:svg.w-3.h-3.transition
                {:xmlns "http://www.w3.org/2000/svg" :fill "none" :viewBox "0 0 24 24" :stroke-width "1.5" :stroke "currentColor"
                 :class (if expanded? "rotate-90" "rotate-0")}
                [:path {:stroke-linecap "round" :stroke-linejoin "round" :d "M8.25 4.5l7.5 7.5-7.5 7.5"}]]]
              [:a.py-1.flex.flex-auto.gap-1.group-hover:text-indigo-700.dark:group-hover:text-white.hover:underline.decoration-indigo-300.dark:decoration-slate-400.underline-offset-2
               {:href path
                :class (when expanded? "font-medium")
                :on-click (fn [event]
                            (navigate-or-scroll! !state path event)
                            (when mobile?
                              (swap! !state assoc :visible? false)))}
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
                           (navigate-or-scroll! !state path event)
                           (when mobile?
                             (swap! !state assoc :visible? false)))}
              (when emoji
                [:span.flex-shrink-0 emoji])
              [:span (if emoji (subs label (count emoji)) label)]])
           (when (and (seq items) expanded?)
             [:div.relative
              {:class (str "ml-[16px] "
                           (when expanded? "mb-2"))}
              (when expanded?
                [:span.absolute.top-0.border-l.border-slate-300.dark:border-slate-600
                 {:class "left-[2px] bottom-[8px]"}])
              [navbar-items !state items (vec (conj update-at i :items))]])]))
      items))))

(defn navbar [!state]
  (let [{:keys [theme items expandable?]} @!state]
    [:div.relative.overflow-x-hidden.h-full
     [:div.absolute.left-0.top-0.w-full.h-full.overflow-y-auto
      {:class (theme-class theme :project)}
      [:div.px-3.mb-1
       {:class (theme-class theme :heading)}
       "TOC"]
      (if expandable?
        [navbar-items !state items [:items]]
        [toc-items !state items (when (< (count items) 2) {:class "font-medium"})])]]))

(defn toggle-button [!state content & [opts]]
  (let [{:keys [mobile? mobile-open? open?]} @!state]
    [:div.toc-toggle
     (merge {:on-click #(swap! !state assoc
                               (if mobile? :mobile-open? :open?) (if mobile? (not mobile-open?) (not open?))
                               :animation-mode (if mobile? :slide-over :push-in))} opts)
     content]))

(def spring {:type :spring :duration 0.35 :bounce 0.1})

(defn panel [!state content]
  (r/with-let [{:keys [local-storage-key]} @!state
               component-key (or local-storage-key (gensym))
               resize #(swap! !state assoc :mobile? (< js/innerWidth 640) :mobile-open? false)
               ref-fn #(if %
                         (do
                           (when local-storage-key
                             (add-watch !state ::persist
                                        (fn [_ _ old {:keys [open?]}]
                                          (when (not= (:open? old) open?)
                                            (localstorage/set-item! local-storage-key open?)))))
                           (js/addEventListener "resize" resize)
                           (resize))
                         (js/removeEventListener "resize" resize))]
    (let [{:keys [animation-mode hide-toggle? open? mobile-open? mobile? mobile-width theme width]} @!state
          w (if mobile? mobile-width width)]
      [:div.flex.h-screen.toc-panel
       {:ref ref-fn}
       [:> AnimatePresence
        {:initial false}
        (when (and mobile? mobile-open?)
          [:> (.-div m)
           {:key (str component-key "-backdrop")
            :class "fixed z-10 bg-gray-500 bg-opacity-75 left-0 top-0 bottom-0 right-0"
            :initial {:opacity 0}
            :animate {:opacity 1}
            :exit {:opacity 0}
            :on-click #(swap! !state assoc :mobile-open? false)
            :transition spring}])
        (when (or mobile-open? (and (not mobile?) open?))
          [:> (.-div m)
           {:key (str component-key "-nav")
            :style {:width w}
            :class (str "h-screen z-10 flex-shrink-0 fixed "
                        (theme-class theme :slide-over) " "
                        (when mobile?
                          (theme-class theme :slide-over-unpinned)))
            :initial (if (= animation-mode :slide-over) {:x (* w -1)} {:margin-left (* w -1)})
            :animate (if (= animation-mode :slide-over) {:x 0} {:margin-left 0})
            :exit (if (= animation-mode :slide-over) {:x (* w -1)} {:margin-left (* w -1)})
            :transition spring
            :on-animation-start #(swap! !state assoc :animating? true)
            :on-animation-complete #(swap! !state assoc :animating? false)}
           (when-not hide-toggle?
             [toggle-button !state
              (if mobile?
                [:svg.h-5.w-5 {:xmlns "http://www.w3.org/2000/svg" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor" :stroke-width "2"}
                 [:path {:stroke-linecap "round" :stroke-linejoin "round" :d "M6 18L18 6M6 6l12 12"}]]
                [:svg.w-4.w-4 {:xmlns "http://www.w3.org/2000/svg" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor" :stroke-width "2"}
                 [:path {:stroke-linecap "round" :stroke-linejoin "round" :d "M15 19l-7-7 7-7"}]])
              {:class (theme-class theme :toggle)}])
           content])]])))
