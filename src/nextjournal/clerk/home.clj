(ns nextjournal.clerk.home
  {:nextjournal.clerk/visibility {:code :hide :result :hide}}
  (:require [clojure.string :as str]
            [babashka.fs :as fs]
            [nextjournal.clerk :as clerk]
            [nextjournal.clerk.builder :as builder]
            [nextjournal.clerk.index :as index]))

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
(defonce !filter (atom ""))

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
  [:div.mt-6
   (clerk/with-viewer index/filter-input-viewer `!filter)]
  [:div.flex.mt-6.border-t
   (when-let [index-paths (:paths @index/!paths)]
     [:div {:class "w-1/2 border-r pt-6 pr-6"}
      [:h4.text-lg "Static Build Index"]
      (clerk/with-viewer index/index-viewer (filter (partial index/query-fn @!filter) index-paths))])
   [:div {:class "w-1/2 pt-6 pl-6"}
    [:h4.text-lg "All Notebooks"]
    (clerk/with-viewer index/index-viewer (filter (partial index/query-fn @!filter) @!notebooks))]]])
