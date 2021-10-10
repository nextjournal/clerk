(ns nextjournal.clerk.static-app
  (:require [nextjournal.clerk.sci-viewer :as sci-viewer]
            [reagent.core :as r]
            [reagent.dom :as rdom]
            [reitit.frontend :as rf]
            [reitit.frontend.easy :as rfe]))

(defonce path->doc
  (r/atom {}))

(defn show [{:keys [path]}]
  (sci-viewer/reset-doc (@path->doc path))
  [:<>
   [:div.flex.flex-col.items-center
    [:div.mt-8.flex.items-center.text-xs.w-full.max-w-prose.px-8.sans-serif.text-gray-400
     [:a.hover:text-indigo-500 {:href "/build/"} "Back to index"]
     [:span.mx-1 "/"]
     [:a.hover:text-indigo-500 {:href "/build/"} "Generated with Clerk."]]]
   [sci-viewer/root]])

(defn index [_]
  [:div.bg-gray-100.flex.items-center.justify-center.overflow-y-auto.w-screen.h-screen
   [:div.mt-12 {:style {:min-width 500}}
    [:div.bg-white.shadow-lg.rounded-lg.border
     [:div.px-8.py-3
      [:h1.text-xl "Clerk"]]
     (into [:ul]
           (map (fn [path]
                  [:li.border-t
                   [:a.pl-8.pr-4.py-2.flex.w-full.items-center.justify-between.hover:bg-indigo-50
                    {:href (rfe/href ::show {:path path})}
                    [:span.text-md.monospace.whitespace-nowrap path]
                    [:svg.h-4.w-4 {:xmlns "http://www.w3.org/2000/svg" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
                     [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M9 5l7 7-7 7"}]]]]))
           (keys @path->doc))]
    [:div.mt-4.text-xs.text-gray-400.sans-serif.px-8
     [:a.hover:text-indigo-600
      {:href "https://github.com/nextjournal/clerk"}
      "Generated with Clerk."]]]])


(def routes
  [["/*path" {:name ::show :view show}]
   ["/" {:name ::index :view index}]])

(defonce router
  (rf/router routes))

(defonce match (r/atom nil))

(defn root []
  (let [{:keys [data path-params] :as match} @match
        {:keys [view]} data]
    [:div.flex.h-screen.bg-white
     [:div.h-screen.overflow-y-auto.flex-auto.devcards-content.bg-gray-50
      (if view
        [view (merge data path-params)]
        [:pre (pr-str match)])]]))

(defn ^:dev/after-load mount []
  (when-let [el (js/document.getElementById "clerk-static-app")]
    (rdom/render [root] el)))

(defn ^:export init [docs]
  (reset! path->doc docs)
  (rfe/start! router #(reset! match %1) {:use-fragment true})
  (mount))
