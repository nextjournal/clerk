(ns nextjournal.clerk.static-app
  (:require [clojure.string :as str]
            [clojure.set :as set]
            [nextjournal.clerk.sci-viewer :as sci-viewer]
            [reagent.core :as r]
            [reagent.dom :as rdom]
            [reitit.frontend :as rf]
            [reitit.frontend.easy :as rfe]
            [sci.core :as sci]))

(defonce !state
  (r/atom {}))

(defn doc-url [path]
  (let [{:keys [path->url path-prefix bundle?]} @!state
        url (path->url path)]
    (if bundle?
      (str "#/" url)
      (str "/" path-prefix url))))

(defn show [{:keys [path]}]
  (sci-viewer/set-state {:doc (get-in @!state [:path->doc path])})
  [:<>
   [:div.flex.flex-col.items-center
    [:div.mt-8.text-xs.w-full.max-w-prose.px-8.sans-serif.text-gray-400
     (when (not= "" path)
      [:<>
       [:a.hover:text-indigo-500.font-medium.border-b.border-dotted.border-gray-300
        {:href (doc-url "")} "Back to index"]
       [:span.mx-1 "/"]])
     [:span
      "Generated with "
      [:a.hover:text-indigo-500.font-medium.border-b.border-dotted.border-gray-300
       {:href "https://github.com/nextjournal/clerk"} "Clerk"]
      #_#_
      " from "
      (let [file-name "rule_30.clj"
            sha "d6b5535"]
        [:a.hover:text-indigo-500.font-medium.border-b.border-dotted.border-gray-300
         {:href "#"} file-name "@" [:span.tabular-nums sha]])]]]
   [sci-viewer/root]])

(defn index [_]
  [:div.bg-gray-100.flex.justify-center.overflow-y-auto.w-screen.h-screen.p-4.md:p-0
   [:div.md:my-12.w-full.md:max-w-lg
    [:div.bg-white.shadow-lg.rounded-lg.border
     [:div.px-4.md:px-8.py-3
      [:h1.text-xl "Clerk"]]
     (into [:ul]
           (map (fn [path]
                  [:li.border-t
                   [:a.pl-4.md:pl-8.pr-4.py-2.flex.w-full.items-center.justify-between.hover:bg-indigo-50
                    {:href (doc-url path)}
                    [:span.text-sm.md:text-md.monospace.flex-auto.block.truncate path]
                    [:svg.h-4.w-4.flex-shrink-0 {:xmlns "http://www.w3.org/2000/svg" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
                     [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M9 5l7 7-7 7"}]]]]))
           (sort (:paths @!state)))]
    [:div.my-4.md:mb-0.text-xs.text-gray-400.sans-serif.px-4.md:px-8
     [:a.hover:text-indigo-600
      {:href "https://github.com/nextjournal/clerk"}
      "Generated with Clerk."]]]])

(defn get-routes [docs]
  (let [index? (contains? docs "")]
    [["/*path" {:name ::show :view show}]
     ["/" {:name ::index :view (if index? show index) :path ""}]]))


(defonce match (r/atom nil))

(defn root []
  (let [{:keys [data path-params] :as match} @match
        {:keys [view]} data]
    [:div.flex.h-screen.bg-white
     [:div.h-screen.overflow-y-auto.flex-auto
      (if view
        [view (merge data path-params)]
        [:pre (pr-str match)])]]))

(defn ^:dev/after-load mount []
  (when-let [el (js/document.getElementById "clerk-static-app")]
    (rdom/render [root] el)))

;; support cases
;; - support backlinks to github with sha
;; further out:
;; - dropping .html extension
;; - client-side loading of edn notebook representation
;; - jit compiling css
;; - support viewing source clojure/markdown file (opt-in)

(defn ^:export init [{:as state :keys [bundle? path->doc path->url current-path]}]
  (let [url->doc (set/rename-keys path->doc path->url)]
    (reset! !state (assoc state :path->doc url->doc))
    (sci/alter-var-root sci-viewer/doc-url (constantly doc-url))
    (if bundle?
      (let [router (rf/router (get-routes url->doc))]
        (rfe/start! router #(reset! match %1) {:use-fragment true}))
      (reset! match {:data {:view (if (str/blank? current-path) index show)} :path-params {:path (path->url current-path)}}))
    (mount)))
