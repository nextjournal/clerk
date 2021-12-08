(ns nextjournal.clerk.static-app
  (:require [nextjournal.clerk.sci-viewer :as sci-viewer]
            [lambdaisland.uri.normalize :as normalize-uri]
            [reagent.core :as r]
            [reagent.dom :as rdom]
            [reitit.frontend :as rf]
            [reitit.frontend.easy :as rfe]
            [clojure.string :as str]))

(defonce path->doc
  (r/atom {}))

(defn show [{:keys [path]}]
  (sci-viewer/reset-doc (@path->doc path))
  [:<>
   [:div.flex.flex-col.items-center
    [:div.mt-8.flex.items-center.text-xs.w-full.max-w-prose.px-8.sans-serif.text-gray-400
     [:a.hover:text-indigo-500 {:href (rfe/href ::index)} "Back to index"]
     [:span.mx-1 "/"]
     [:a.hover:text-indigo-500 {:href "https://github.com/nextjournal/clerk"} "Generated with Clerk."]]]
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
                    {:href (normalize-uri/percent-decode (rfe/href ::show {:path path}))}
                    [:span.text-sm.md:text-md.monospace.flex-auto.block.truncate path]
                    [:svg.h-4.w-4.flex-shrink-0 {:xmlns "http://www.w3.org/2000/svg" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
                     [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M9 5l7 7-7 7"}]]]]))
           (sort (keys @path->doc)))]
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

(defn strip-index [path]
  (str/replace path #"(^|.*/)(index\.(clj|cljc|md))$" "$1"))

(defn ^:export init [docs]
  (reset! path->doc (into {} (map (fn [[path doc]] [(strip-index path) doc]) docs)))
  (let [router (rf/router (get-routes @path->doc))]
    (rfe/start! router #(reset! match %1) {:use-fragment true}))
  (mount))
