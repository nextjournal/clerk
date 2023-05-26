(ns nextjournal.clerk.home
  {:nextjournal.clerk/visibility {:code :hide :result :hide}}
  (:require [clojure.string :as str]
            [babashka.fs :as fs]
            [nextjournal.clerk :as clerk]
            [nextjournal.clerk.builder :as builder]
            [nextjournal.clerk.viewer :as v]))

(defn glob-notebooks []
  (let [ignored-paths (into #{} (map fs/path) #{"node_modules" "target"})]
    (into []
          (comp
           (remove #(some ignored-paths (fs/components %)))
           (map str))
          (fs/glob "." "**.{clj,md}"))))

^::clerk/no-cache
(def !notebooks
  (atom (glob-notebooks)))

^::clerk/sync
(defonce !filter (atom {}))

#_(reset! !filter {})

(defn query-fn [query path]
  (str/includes? (str/lower-case path) (str/lower-case (or query ""))))

(defn filtered+sorted-paths [{:keys [paths query]}]
  (vec
   (mapcat
    (fn [[dir files]]
      (sort files))
    (sort-by key
             (group-by (fn [path]
                         (str/join fs/file-separator (butlast (fs/path path))))
                       (filter (partial query-fn query) paths))))))

(defn select-path [move]
  (let [{:as filter :keys [selected-path]} @!filter
        paths (filtered+sorted-paths (merge {:paths @!notebooks} filter))
        index (.indexOf paths selected-path)
        next-index (move index)]
    (when (contains? paths next-index)
      (swap! !filter assoc :selected-path (get paths next-index)))))

#_(add-watch !filter :empty-selected-path
             (fn [_ _ old-filter {:as filter :keys [selected-path]}]
               (when-not (contains? filter :selected-path)
                 (swap! !filter assoc :selected-path (first (filtered+sorted-paths (merge {:paths @!notebooks} filter)))))))

(defn show-path []
  (when-let [path (:selected-path @!filter)]
    (clerk/show! path)))

(def index-viewer
  {:render-fn '(fn [{:keys [directories query selected-path]} opts]
                 [:div.not-prose.font-sans
                  (into [:div]
                        (map
                         (fn [[dir files]]
                           [:div.mb-6
                            (when (seq dir)
                              [:div.flex.items-center.gap-2.mb-1
                               [:svg {:xmlns "http://www.w3.org/2000/svg" :fill "none" :viewBox "0 0 24 24" :stroke-width "1.5" :stroke "currentColor" :class "w-4 h-4"}
                                [:path {:stroke-linecap "round" :stroke-linejoin "round" :d "M3.75 9.776c.112-.017.227-.026.344-.026h15.812c.117 0 .232.009.344.026m-16.5 0a2.25 2.25 0 00-1.883 2.542l.857 6a2.25 2.25 0 002.227 1.932H19.05a2.25 2.25 0 002.227-1.932l.857-6a2.25 2.25 0 00-1.883-2.542m-16.5 0V6A2.25 2.25 0 016 3.75h3.879a1.5 1.5 0 011.06.44l2.122 2.12a1.5 1.5 0 001.06.44H18A2.25 2.25 0 0120.25 9v.776"}]]
                               [:span.font-bold dir]])
                            (into [:ul
                                   {:class (when (seq dir) "ml-[20px]")}]
                                  (map (fn [file]
                                         [:li.mb-1
                                          [:a.hover:underline.inline-flex.items-center
                                           {:class (str
                                                    "px-[7px] py-[2px] "
                                                    (if (= selected-path file)
                                                      "bg-blue-700 dark:bg-slate-600 rounded-md text-white"
                                                      "text-blue-600 dark:text-white"))
                                            :href (nextjournal.clerk.viewer/doc-url file)}
                                           file
                                           (when (= selected-path file)
                                             [:span.font-inter.opacity-60.relative.ml-1 {:class "top-[2px]"} "↩︎"])]]))
                                  (sort files))]))
                        directories)])
   :transform-fn (comp
                  clerk/mark-presented
                  (clerk/update-val (fn [{:as index :keys [paths]}]
                                      (assoc index :directories (sort-by
                                                                 key
                                                                 (group-by (fn [path]
                                                                             (str/join fs/file-separator (butlast (fs/path path)))) paths))))))})

(def filter-input-viewer
  (assoc v/viewer-eval-viewer
         :var-from-def true
         :render-fn '(fn [!state]
                       (let [!input-el (nextjournal.clerk.render.hooks/use-ref nil)]
                         (nextjournal.clerk.render.hooks/use-effect
                          (fn []
                            (let [keydown-handler (fn [e]
                                                    (when @!input-el
                                                      (let [native-scroll-modifier? (or (.-metaKey e) (.-altKey e) (.-ctrlKey e))]
                                                        (cond
                                                          (and (.-metaKey e) (= (.-key e) "j"))
                                                          (.focus @!input-el)
                                                          (and (= @!input-el js/document.activeElement) (= (.-key e) "Escape"))
                                                          (.blur @!input-el)
                                                          (and (= (.-key e) "ArrowDown") (not native-scroll-modifier?))
                                                          (do
                                                            (.preventDefault e)
                                                            (nextjournal.clerk.render/clerk-eval '(select-path inc)))
                                                          (and (= (.-key e) "ArrowUp") (not native-scroll-modifier?))
                                                          (do
                                                            (.preventDefault e)
                                                            (nextjournal.clerk.render/clerk-eval '(select-path dec)))
                                                          (= (.-key e) "Enter")
                                                          (do
                                                            (.preventDefault e)
                                                            (nextjournal.clerk.render/clerk-eval '(show-path)))))))]
                              (js/document.addEventListener "keydown" keydown-handler)
                              #(js/document.removeEventListener "keydown" keydown-handler)))
                          [!input-el])
                         [:div.relative
                          [:input.pl-8.py-2.bg-white.dark:bg-slate-800.rounded-lg.font-medium.font-sans.border.dark:border-slate-700.w-full.shadow-inner.dark:focus:outline-none.dark:focus:border-white.dark:text-white.transition-all
                           {:class "pr-[60px]"
                            :type :text
                            :placeholder "Type to filter…"
                            :value (:query @!state "")
                            :on-input (fn [e] (swap! !state #(-> %
                                                                (assoc :query (.. e -target -value))
                                                                (dissoc :selected-path))))
                            :ref !input-el}]
                          [:div.text-slate-400.absolute
                           {:class "left-[10px] top-[11px]"}
                           [:svg
                            {:xmlns "http://www.w3.org/2000/svg" :fill "none" :viewBox "0 0 24 24"
                             :stroke-width "1.5" :stroke "currentColor" :class "w-[20px] h-[20px]"}
                            [:path {:stroke-linecap "round" :stroke-linejoin "round" :d "M21 21l-5.197-5.197m0 0A7.5 7.5 0 105.196 5.196a7.5 7.5 0 0010.607 10.607z"}]]]
                          [:div.absolute.font-inter.text-slate-400.dark:bg-slate-900.text-sm.font-medium.border.dark:border-slate-700.rounded-md.tracking-wide.px-1.shadow-sm.dark:shadow.pointer-events-none
                           {:class "right-[10px] top-[9px]"}
                           "⌘J"]]))))

{::clerk/visibility {:result :show}}

^{::clerk/css-class ["w-full" "m-0"]}
(clerk/html
 [:div.max-w-prose.px-8.mx-auto
  [:div.md:flex.md:justify-between.text-center.md:text-left
   [:h1 "👋 Welcome to Clerk!"]
   #_[:div.text-sm.md:text-xs.font-sans.md:text-right.mt-1
      [:span.font-bold.block
       "You’re running Clerk " [:a {:href "#"} "v0.12.707"] "."]
      [:span "The newest version is " [:a {:href "#"} "v0.12.707"] ". " [:a {:href "#"} "What’s changed?"]]]]
  [:div.rounded-lg.border-2.border-amber-100.bg-amber-50.dark:border-slate-600.dark:bg-slate-800.dark:text-slate-100.px-8.py-4.mx-auto.text-center.font-sans.mt-6.md:mt-4
   [:div.font-medium
    "Call "
    [:span.font-mono.text-sm.bg-white.bg-amber-100.border.border-amber-300.relative.dark:bg-slate-900.dark:border-slate-600.rounded.font-bold
     {:class "px-[4px] py-[1px] -top-[1px] mx-[2px]"}
     "nextjournal.clerk/show!"]
    " from your REPL to make a notebook appear!"]
   [:div.mt-2.text-sm "⚡️ This works best when you " [:a {:href "https://book.clerk.vision/#editor-integration"} "set up your editor to use a key binding for this!"]]]
  [:div.rounded-lg.border-2.border-indigo-100.bg-indigo-50.dark:border-slate-600.dark:bg-slate-800.dark:text-slate-100.px-8.py-4.mt-6.text-center.font-sans
   [:div.font-medium.md:flex.items-center.justify-center
    [:span.text-xl.relative {:class "top-[2px] mr-2"} "📖"]
    [:span "New to Clerk? Learn all about it in " [:a {:href "https://book.clerk.vision"} [:span.block.md:inline "The Book of Clerk."]]]]
   #_
   [:div.mt-2.text-sm
    "Here are some handy links:"
    [:a.ml-3 {:href "#"} "🚀 Getting Started"]
    [:a.ml-3 {:href "#"} "🔍 Viewers"]
    [:a.ml-3 {:href "#"} "🙈 Controlling Visibility"]]]
  #_[:div.mt-6
     (clerk/with-viewer filter-input-viewer `!filter)]
  [:div.flex.mt-6.border-t.font-sans
   [:div {:class (str "w-1/2 pt-6 " (when-not (seq @!filter) "pr-6 border-r"))}
    [:h4.text-lg "All Notebooks"]
    (let [{:keys [query selected-path]} @!filter]
      (clerk/with-viewer index-viewer {:paths (filter (partial query-fn query) @!notebooks)
                                       :selected-path selected-path}))]
   (when-not (seq (:query @!filter))
     [:div {:class "w-1/2 pt-6 pl-6"}
      [:h4.text-lg "Static Build Index"]
      (let [{:keys [paths error]} (builder/index-paths)]
        (cond
          error [:div {:class "-mx-8"} (clerk/md error)]
          paths (let [{:keys [query]} @!filter]
                  (clerk/with-viewer index-viewer {:paths (filter (partial query-fn query) paths)}))))])]])
