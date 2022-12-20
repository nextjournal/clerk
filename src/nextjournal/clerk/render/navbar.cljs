(ns nextjournal.clerk.render.navbar
  (:require ["emoji-regex" :as emoji-regex]
            ["framer-motion" :as framer-motion :refer [motion AnimatePresence]]
            [nextjournal.ui.components.localstorage :as ls]
            [applied-science.js-interop :as j]
            [clojure.string :as str]
            [reagent.core :as r]))

(def emoji-re (emoji-regex))

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
  (let [{:keys [theme]} @!state]
    (into
     [:div]
     (map
      (fn [{:keys [path title items]}]
        [:<>
         [:a.flex
          {:href path
           :class (theme-class theme :item)
           :on-click (fn [event]
                       (stop-event! event)
                       (scroll-to-anchor! !state path))}
          [:div (merge {} options) title]]
         (when (seq items)
           [:div.ml-3
            [toc-items !state items]])])
      items))))

(defn navbar-items [!state items update-at]
  (let [{:keys [mobile? theme]} @!state]
    (into
     [:div]
     (map-indexed
      (fn [i {:keys [path title expanded? loading? items toc]}]
        (let [label (or title (str/capitalize (last (str/split path #"/"))))
              emoji (when (zero? (.search label emoji-re))
                      (first (.match label emoji-re)))]
          [:<>
           (if (seq items)
             [:div.flex.cursor-pointer
              {:class (theme-class theme :expandable)
               :on-click (fn [event]
                           (stop-event! event)
                           (swap! !state assoc-in (vec (conj update-at i :expanded?)) (not expanded?)))}
              [:div.flex.items-center.justify-center.flex-shrink-0
               {:class "w-[20px] h-[20px] mr-[4px]"}
               [:svg.transform.transition
                {:viewBox "0 0 100 100"
                 :class (str (theme-class theme :triangle) " "
                             "w-[10px] h-[10px] "
                             (if expanded? "rotate-180" "rotate-90"))}
                [:polygon {:points "5.9,88.2 50,11.8 94.1,88.2 " :fill "currentColor"}]]]
              [:div label]]
             [:a.flex
              {:href path
               :class (theme-class theme :item)
               :on-click (fn []
                           (when toc
                             (swap! !state assoc-in (vec (conj update-at i :loading?)) true)
                             (js/setTimeout
                              (fn []
                                (swap! !state #(-> (assoc-in % (vec (conj update-at i :loading?)) false)
                                                   (assoc :toc toc))))
                              500))
                           (when mobile?
                             (swap! !state assoc :visible? false)))}
              [:div.flex.items-center.justify-center.flex-shrink-0
               {:class "w-[20px] h-[20px] mr-[4px]"}
               (if loading?
                 [:svg.animate-spin.h-3.w-3.text-slate-500.dark:text-slate-400
                  {:xmlns "http://www.w3.org/2000/svg" :fill "none" :viewBox "0 0 24 24"}
                  [:circle.opacity-25 {:cx "12" :cy "12" :r "10" :stroke "currentColor" :stroke-width "4"}]
                  [:path.opacity-75 {:fill "currentColor" :d "M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"}]]
                 (if emoji
                   [:div emoji]
                   [:svg.h-4.w-4
                    {:xmlns "http://www.w3.org/2000/svg" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"
                     :class (theme-class theme :icon)}
                    [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"}]]))]
              [:div
               (if emoji
                 (subs label (count emoji))
                 label)]])
           (when (and (seq items) expanded?)
             [:div.ml-3
              [navbar-items !state items (vec (conj update-at i :items))]])]))
      items))))

(defn navbar [!state]
  (let [{:keys [items theme toc]} @!state
        items? (seq items)]
    [:div.relative.overflow-x-hidden.h-full
     (when items?
       [:div.absolute.left-0.top-0.w-full.h-full.overflow-y-auto.transform.transition.pb-10
        {:class (str (theme-class theme :project) " "
                     (if toc "-translate-x-full" "translate-x-0"))}
        [:div.px-3.mb-1
         {:class (theme-class theme :heading)}
         "Project"]
        [navbar-items !state (:items @!state) [:items]]])
     [:div.absolute.left-0.top-0.w-full.h-full.overflow-y-auto.transform
      {:class (str (when items? "transition ")
                   (theme-class theme :toc) " "
                   (if toc "translate-x-0" "translate-x-full"))}
      (if (and (seq items) (seq toc))
        [:div.px-3.py-1.cursor-pointer
         {:class (theme-class theme :back)
          :on-click #(swap! !state dissoc :toc)}
         "← Back to project"]
        [:div.px-3.mb-1
         {:class (theme-class theme :heading)}
         "TOC"])
      [toc-items !state toc (when (< (count toc) 2) {:class "font-medium"})]]]))

(defn toggle-button [!state content & [opts]]
  (let [{:keys [mobile? mobile-open? open?]} @!state]
    [:div
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
                                            (ls/set-item! local-storage-key open?)))))
                           (js/addEventListener "resize" resize)
                           (resize))
                         (js/removeEventListener "resize" resize))]
    (let [{:keys [animation-mode hide-toggle? open? mobile-open? mobile? mobile-width theme width]} @!state
          w (if mobile? mobile-width width)]
      [:div.flex.h-screen
       {:ref ref-fn}
       [:> AnimatePresence
        {:initial false}
        (when (and mobile? mobile-open?)
          [:> (.-div motion)
           {:key (str component-key "-backdrop")
            :class "fixed z-10 bg-gray-500 bg-opacity-75 left-0 top-0 bottom-0 right-0"
            :initial {:opacity 0}
            :animate {:opacity 1}
            :exit {:opacity 0}
            :on-click #(swap! !state assoc :mobile-open? false)
            :transition spring}])
        (when (or mobile-open? (and (not mobile?) open?))
          [:> (.-div motion)
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
