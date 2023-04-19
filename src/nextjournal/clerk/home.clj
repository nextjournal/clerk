(ns nextjournal.clerk.home
  {:nextjournal.clerk/visibility {:code :hide :result :hide}}
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [nextjournal.clerk :as clerk]))

{::clerk/visibility {:result :show}}

^{::clerk/css-class ["w-full" "m-0"]}
(clerk/html
 [:div.max-w-prose.px-8.mx-auto
  [:div.md:flex.md:justify-between.px-8.text-center.md:text-left
   [:h1 "👋 Welcome to Clerk!"]
   [:div.text-sm.md:text-xs.font-sans.md:text-right.mt-1
    [:span.font-bold.block
     "You’re running Clerk " [:a {:href "#"} "v0.12.707"] "."]
    [:span "The newest version is " [:a {:href "#"} "v0.12.707"] ". " [:a {:href "#"} "What’s changed?"]]]]
  [:div.rounded-lg.border-2.border-amber-100.bg-amber-50.px-8.pt-3.pb-4.mx-auto.text-center.font-sans.mt-6.md:mt-4
   [:div.font-medium
    "Call " [:span.font-mono.text-sm.bg-white.bg-opacity-80.mx-1.font-bold "nextjournal.clerk/show"]
    " from your REPL to make a notebook appear!"]
   [:div.mt-2.text-sm "⚡️ This works best when you " [:a {:href "#"} "set up your editor to use a key binding for this!"]]]
  [:div.rounded-lg.border-2.border-indigo-100.bg-indigo-50.px-8.pt-3.pb-4.mt-6.text-center.font-sans
   [:div.font-medium "📖 New with Clerk? Learn all about it in " [:a {:href "#"} "The Book of Clerk"] "."]
   [:div.mt-2.text-sm
    "Here are some handy links:"
    [:a.ml-3 {:href "#"} "🚀 Getting Started"]
    [:a.ml-3 {:href "#"} "🔍 Viewers"]
    [:a.ml-3 {:href "#"} "🙈 Controlling Visibility"]]]
  [:h4 "Your notebooks"]
  (into
   [:div.md:columns-3.gap-8.font-sans.text-sm
    ]
   (map (fn [file-name]
          [:div
           [:a {:href file-name} file-name]]))
   (filter #(str/ends-with? % ".clj") (map #(.getName %) (file-seq (io/file "notebooks")))))])
