(ns nextjournal.clerk.static-app
  (:require [clojure.string :as str]
            [clojure.set :as set]
            [nextjournal.clerk.sci-viewer :as sci-viewer]
            [nextjournal.devcards :as dc]
            [reagent.core :as r]
            [reagent.dom :as rdom]
            [reitit.frontend :as rf]
            [reitit.frontend.easy :as rfe]
            [sci.core :as sci]))

(defn doc-url [{:keys [path->url path-prefix bundle?]} path]
  (let [url (path->url path)]
    (if bundle?
      (str "#/" url)
      (str "/" path-prefix url))))

(defn show [{:as view-data :git/keys [sha url] :keys [doc path url->path]}]
  (sci-viewer/set-state {:doc doc})
  [:<>
   [:div.flex.flex-col.items-center
    [:div.mt-8.text-xs.w-full.max-w-prose.px-8.sans-serif.text-gray-400
     (when (not= "" path)
       [:<>
        [:a.hover:text-indigo-500.font-medium.border-b.border-dotted.border-gray-300
         {:href (doc-url view-data  "")} "Back to index"]
        [:span.mx-1 "/"]])
     [:span
      "Generated with "
      [:a.hover:text-indigo-500.font-medium.border-b.border-dotted.border-gray-300
       {:href "https://github.com/nextjournal/clerk"} "Clerk"]
      (when (and url sha (contains? url->path path))
        [:<>
         " from "
         [:a.hover:text-indigo-500.font-medium.border-b.border-dotted.border-gray-300
          {:href (str url "/blob/" sha "/" (url->path path))} (url->path path) "@" [:span.tabular-nums (subs sha 0 7)]]])]]]
   [sci-viewer/root]])

(dc/defcard show []
  [show {:git/url "https://github.com/nextjournal/clerk"
         :git/sha "1026e6199f723e0f6ea92301b9678c9cf7024ba0"
         :path "notebooks/hello.clj"
         :paths ["notebooks/hello.clj"],
         :bundle? true,
         :live-js? true,
         :doc {:nextjournal/viewer :clerk/notebook,
               :scope {:namespace :nextjournal.clerk}
               :nextjournal/value [{:nextjournal/value "# Hello Clerk 👋", :nextjournal/viewer :markdown}
                                   {:nextjournal/value "(+ 39 3)", :nextjournal/viewer :code}
                                   {:nextjournal/viewer :clerk/result,
                                    :nextjournal/value {:nextjournal/edn "{:path [], :nextjournal/value 42, :nextjournal/viewer {:render-fn #function+ (fn [x] (v/html [:span.syntax-number.inspected-value (if (js/Number.isNaN x) \"NaN\" (str x))]))}}"}, :path []}],},
         :path->url {"notebooks/hello.clj" "notebooks/hello.clj"}
         :url->path {"notebooks/hello.clj" "notebooks/hello.clj"}}])


(defn index [{:as view-data :keys [paths]}]
  [:div.bg-gray-100.flex.justify-center.overflow-y-auto.w-screen.h-screen.p-4.md:p-0
   [:div.md:my-12.w-full.md:max-w-lg
    [:div.bg-white.shadow-lg.rounded-lg.border
     [:div.px-4.md:px-8.py-3
      [:h1.text-xl "Clerk"]]
     (into [:ul]
           (map (fn [path]
                  [:li.border-t
                   [:a.pl-4.md:pl-8.pr-4.py-2.flex.w-full.items-center.justify-between.hover:bg-indigo-50
                    {:href (doc-url view-data path)}
                    [:span.text-sm.md:text-md.monospace.flex-auto.block.truncate path]
                    [:svg.h-4.w-4.flex-shrink-0 {:xmlns "http://www.w3.org/2000/svg" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
                     [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M9 5l7 7-7 7"}]]]]))
           (sort paths))]
    [:div.my-4.md:mb-0.text-xs.text-gray-400.sans-serif.px-4.md:px-8
     [:a.hover:text-indigo-600
      {:href "https://github.com/nextjournal/clerk"}
      "Generated with Clerk."]]]])

(dc/defcard index []
  [index {:git/url "https://github.com/nextjournal/clerk"
          :git/sha "1026e6199f723e0f6ea92301b9678c9cf7024ba0"
          :paths ["notebooks/hello.clj"],
          :bundle? true,
          :live-js? true,
          :path->url {"notebooks/hello.clj" "notebooks/hello.clj"}
          :url->path {"notebooks/hello.clj" "notebooks/hello.clj"}}])

(defn get-routes [docs]
  (let [index? (contains? docs "")]
    [["/*path" {:name ::show :view show}]
     ["/" {:name ::index :view (if index? show index)}]]))


(defonce !match (r/atom nil))
(defonce !state (r/atom {}))

(defn root []
  (let [{:keys [data path-params] :as match} @!match
        {:keys [view]} data
        view-data (merge @!state data path-params {:doc (get-in @!state [:path->doc (:path path-params "")])})]
    [:div.flex.h-screen.bg-white
     [:div.h-screen.overflow-y-auto.flex-auto
      (if view
        [view view-data]
        [:pre (pr-str match)])]]))

(defn ^:dev/after-load mount []
  (when-let [el (js/document.getElementById "clerk-static-app")]
    (rdom/render [root] el)))

;; next up
;; - jit compiling css
;; - support viewing source clojure/markdown file (opt-in)

(defn ^:export init [{:as state :keys [bundle? path->doc path->url current-path]}]
  (let [url->doc (set/rename-keys path->doc path->url)]
    (reset! !state (assoc state
                          :path->doc url->doc
                          :url->path (set/map-invert path->url)))
    (js/console.log :init state)
    (sci/alter-var-root sci-viewer/doc-url (constantly (partial doc-url @!state)))
    (if bundle?
      (let [router (rf/router (get-routes url->doc))]
        (rfe/start! router #(reset! !match %1) {:use-fragment true}))
      (reset! !match {:data {:view (if (str/blank? current-path) index show)} :path-params {:path (path->url current-path)}}))
    (mount)))
