(ns nextjournal.clerk.index
  {:nextjournal.clerk/visibility {:code :hide :result :hide}
   :nextjournal.clerk/no-cache true}
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as v]
            [nextjournal.clerk.builder :as builder]))

(def !paths (delay (builder/index-paths)))

(def index-item-viewer
  {:pred string?
   :transform-fn (clerk/update-val (fn [path]
                                     (clerk/html
                                      [:li.border-t.first:border-t-0.dark:border-gray-800.odd:bg-slate-50.dark:odd:bg-white
                                       {:class "dark:odd:bg-opacity-[0.03]"}
                                       [:a.pl-4.pr-4.py-2.flex.w-full.items-center.justify-between.hover:bg-indigo-50.dark:hover:bg-gray-700
                                        {:href (clerk/doc-url path)}
                                        [:span.text-sm.md:text-md.monospace.flex-auto.block.truncate path]
                                        [:svg.h-4.w-4.flex-shrink-0 {:xmlns "http://www.w3.org/2000/svg" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
                                         [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M9 5l7 7-7 7"}]]]])))})

(def index-viewer
  {:render-fn '(fn [xs opts]
                 [:div.not-prose
                  [:h1.mb-4 "Clerk"]
                  (into [:ul.border.dark:border-slate-800.rounded-md.overflow-hidden]
                        (nextjournal.clerk.render/inspect-children opts)
                        xs)])
   :transform-fn (fn [wrapped-value]
                   (update wrapped-value :nextjournal/viewers v/add-viewers [index-item-viewer]))})

{::clerk/visibility {:result :show}}
(let [{:keys [paths error]} @!paths]
  (cond
    error (clerk/md error)
    paths (clerk/with-viewer index-viewer paths)))

#_[:div.bg-gray-100.dark:bg-gray-900.flex.justify-center.overflow-y-auto.w-screen.h-screen.p-4.md:p-0
   {:ref ref-fn}
   [:div.fixed.top-2.left-2.md:left-auto.md:right-2.z-10
    [render/dark-mode-toggle !state]]
   [:div.md:my-12.w-full.md:max-w-lg
    [:div.bg-white.dark:bg-gray-800.shadow-lg.rounded-lg.border.dark:border-gray-800.dark:text-white
     [:div.px-4.md:px-8.py-3
      [:h1.text-xl "Clerk"]]
     (into [:ul]
           (map (fn [path]
                  [:li.border-t.dark:border-gray-900
                   [:a.pl-4.md:pl-8.pr-4.py-2.flex.w-full.items-center.justify-between.hover:bg-indigo-50.dark:hover:bg-gray-700
                    {:href (doc-url view-data path)}
                    [:span.text-sm.md:text-md.monospace.flex-auto.block.truncate path]
                    [:svg.h-4.w-4.flex-shrink-0 {:xmlns "http://www.w3.org/2000/svg" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
                     [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M9 5l7 7-7 7"}]]]]))
           (sort paths))]
    [:div.my-4.md:mb-0.text-xs.text-gray-400.sans-serif.px-4.md:px-8
     [:a.hover:text-indigo-600.dark:hover:text-white
      {:href "https://github.com/nextjournal/clerk"}
      "Generated with Clerk."]]]]
