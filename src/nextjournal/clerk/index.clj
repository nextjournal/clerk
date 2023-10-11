(ns nextjournal.clerk.index
  {:nextjournal.clerk/visibility {:code :hide :result :hide}
   :nextjournal.clerk/no-cache true}
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [nextjournal.clerk :as clerk]
            [nextjournal.clerk.paths :as paths]
            [nextjournal.clerk.viewer :as v]))

(def !paths (delay (paths/index-paths)))

(def index-item-viewer
  {:pred string?
   :transform-fn (clerk/update-val (fn [path]
                                     (clerk/html
                                      [:li.border-t.first:border-t-0.dark:border-gray-800.odd:bg-slate-50.dark:odd:bg-white
                                       {:class "dark:odd:bg-opacity-[0.03]"}
                                       [:a.pl-4.pr-4.py-2.flex.w-full.items-center.justify-between.hover:bg-indigo-50.dark:hover:bg-gray-700
                                        {:href (clerk/doc-url (fs/strip-ext path))}
                                        [:span.text-sm.md:text-md.monospace.flex-auto.block.truncate path]
                                        [:svg.h-4.w-4.flex-shrink-0 {:xmlns "http://www.w3.org/2000/svg" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
                                         [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M9 5l7 7-7 7"}]]]])))})

(def index-viewer
  {:render-fn '(fn [xs opts]
                 [:div.not-prose
                  (into [:ul.border.dark:border-slate-800.rounded-md.overflow-hidden]
                        (nextjournal.clerk.render/inspect-children opts)
                        xs)])
   :transform-fn (fn [wrapped-value]
                   (update wrapped-value :nextjournal/viewers v/add-viewers [index-item-viewer]))})

{::clerk/visibility {:result :show}}

(clerk/html [:h1 {:style {:margin-bottom "-0.45rem"}} (str (last (fs/cwd)))])

(let [{:keys [paths error]} @!paths]
  (cond
    error (clerk/md {::clerk/css-class [:font-sans :max-w-prose :w-full]} error)
    paths (clerk/with-viewer index-viewer paths)))

(clerk/html
 [:div.text-xs.text-slate-400.font-sans.mb-8.not-prose
  [:span.block.font-medium "This index page was automatically generated by Clerk."]
  "You can customize it by adding a index.clj file to your project’s root directory. See " [:a.text-blue-600.dark:text-blue-300.hover:underline {:href "https://book.clerk.vision/#static-building"} "Static Publishing"] " in the " [:a.text-blue-600.dark:text-blue-300.hover:underline {:href "http://book.clerk.vision"} "Book of Clerk"] "."])
