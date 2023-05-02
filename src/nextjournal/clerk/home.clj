(ns nextjournal.clerk.home
  {:nextjournal.clerk/visibility {:code :hide :result :hide}}
  (:require [clojure.string :as str]
            [babashka.fs :as fs]
            [nextjournal.clerk :as clerk]))

(defn glob-notebooks []
  (let [ignored-paths (into #{} (map fs/path) #{"node_modules" "target"})]
    (into []
          (remove #(some ignored-paths (fs/components %)))
          (fs/glob "." "**.{clj,md}"))))

(defn ->tree [data]
  (map (fn [[k vs]] (cons k (->tree (keep next vs))))
       (group-by first data)))

(defn file? [s]
  (prn "file?" s)
  (or (str/ends-with? s ".clj") (str/ends-with? s ".md")))

(defn dir-view [path cs]
  (let [c (first cs)]
    (if (file? c)
      [:div.mb-1
       [:a.block.text-blue-600.hover:underline.flex.items-center.gap-1
        {:href (clerk/doc-url (str (str/join fs/file-separator path) fs/file-separator c))}
        [:svg.flex-shrink-0 {:xmlns "http://www.w3.org/2000/svg" :fill "none" :viewBox "0 0 24 24" :stroke-width "1.5" :stroke "currentColor" :class "w-3 h-3"}
         [:path {:stroke-linecap "round" :stroke-linejoin "round" :d "M19.5 14.25v-2.625a3.375 3.375 0 00-3.375-3.375h-1.5A1.125 1.125 0 0113.5 7.125v-1.5a3.375 3.375 0 00-3.375-3.375H8.25m2.25 0H5.625c-.621 0-1.125.504-1.125 1.125v17.25c0 .621.504 1.125 1.125 1.125h12.75c.621 0 1.125-.504 1.125-1.125V11.25a9 9 0 00-9-9z"}]]
        c]]
      [:div
       [:span.block.font-medium.mb-1.flex.items-center.gap-1
        [:svg {:xmlns "http://www.w3.org/2000/svg" :fill "none" :viewBox "0 0 24 24" :stroke-width "1.5" :stroke "currentColor" :class "w-3 h-3"}
         [:path {:stroke-linecap "round" :stroke-linejoin "round" :d "M3.75 9.776c.112-.017.227-.026.344-.026h15.812c.117 0 .232.009.344.026m-16.5 0a2.25 2.25 0 00-1.883 2.542l.857 6a2.25 2.25 0 002.227 1.932H19.05a2.25 2.25 0 002.227-1.932l.857-6a2.25 2.25 0 00-1.883-2.542m-16.5 0V6A2.25 2.25 0 016 3.75h3.879a1.5 1.5 0 011.06.44l2.122 2.12a1.5 1.5 0 001.06.44H18A2.25 2.25 0 0120.25 9v.776"}]]
        c]
       (into [:div.ml-2] (map (partial dir-view (conj path c)) (sort-by first (rest cs))))])))

^::clerk/no-cache
(def !notebooks
  (atom (glob-notebooks)))

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
  [:h4.text-lg "Your notebooks"]
  (into [:div.font-sans.font-normal.not-prose.flex.flex-col.gap-2]
        (->> (map #(map str (fs/components %)) @!notebooks)
             ->tree
             (sort-by ffirst)
             (map (partial dir-view []))
             (partition 4)
             (map (fn [col] (into [:div.grid.grid-cols-4.gap-4] col)))))])

