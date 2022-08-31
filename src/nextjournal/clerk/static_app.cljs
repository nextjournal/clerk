(ns nextjournal.clerk.static-app
  (:require [clojure.string :as str]
            [clojure.set :as set]
            [nextjournal.clerk.viewer :as v]
            [nextjournal.clerk.sci-viewer :as sci-viewer]
            [nextjournal.ui.components.localstorage :as ls]
            [nextjournal.devcards :as dc]
            [reagent.core :as r]
            [reagent.dom :as rdom]
            [reitit.frontend :as rf]
            [reitit.frontend.easy :as rfe]
            [sci.core :as sci]))

(defn doc-url [{:keys [path->url current-path bundle?]} path]
  (let [url (path->url path)]
    (if bundle?
      (str "#/" url)
      (let [url (cond-> url
                  (and (= (.. js/document -location -protocol) "file:")
                       (or (nil? url)
                           (str/ends-with? url "/")))
                  (str "index.html"))
            dir-depth (get (frequencies current-path) \/ 0)
            relative-root (str/join (repeat dir-depth "../"))]
        (str relative-root url)))))

(defn hiccup [hiccup]
  {:nextjournal/viewer sci-viewer/html-render
   :nextjournal/value hiccup})

(defn show [{:as view-data :git/keys [sha url] :keys [doc path url->path]}]
  (let [header [:div.mb-8.text-xs.sans-serif.text-gray-400.not-prose
                (when (not= "" path)
                  [:<>
                   [:a.hover:text-indigo-500.dark:hover:text-white.font-medium.border-b.border-dotted.border-gray-300
                    {:href (doc-url view-data "")} "Back to index"]
                   [:span.mx-1 "/"]])
                [:span
                 "Generated with "
                 [:a.hover:text-indigo-500.dark:hover:text-white.font-medium.border-b.border-dotted.border-gray-300
                  {:href "https://github.com/nextjournal/clerk"} "Clerk"]
                 (when (and url sha (contains? url->path path))
                   [:<>
                    " from "
                    [:a.hover:text-indigo-500.dark:hover:text-white.font-medium.border-b.border-dotted.border-gray-300
                     {:href (str url "/blob/" sha "/" (url->path path))} (url->path path) "@" [:span.tabular-nums (subs sha 0 7)]]])]]]
    (sci-viewer/set-state {:doc (cond-> doc
                                  (vector? (get-in doc [:nextjournal/value :blocks]))
                                  (update-in [:nextjournal/value :blocks] (partial into [(hiccup header)])))})
    [sci-viewer/root]))

(dc/defcard show []
  [show {:git/url "https://github.com/nextjournal/clerk"
         :git/sha "1026e6199f723e0f6ea92301b9678c9cf7024ba0"
         :path "notebooks/hello.clj"
         :paths ["notebooks/hello.clj"],
         :bundle? true,
         :live-js? true,
         :doc {:nextjournal/value {:blocks [#:nextjournal{:value " # Hello, Clerk 👋\n", :viewer :markdown}
                                            #:nextjournal{:value "(+ 39 3)", :viewer :code}
                                            {:nextjournal/viewer :clerk/result,
                                             :nextjournal/value #:nextjournal{:edn "{:path [], :nextjournal/value 42, :nextjournal/viewer {:render-fn #viewer-fn (fn [x] (v/html [:span.cmt-number.inspected-value (if (js/Number.isNaN x) \"NaN\" (str x))]))}}"},
                                             :path []}],
                                   :title "Hello, Clerk 👋"},
               :nextjournal/viewer :clerk/notebook,
               :scope {:namespace :nextjournal.clerk}},
         :path->url {"notebooks/hello.clj" "notebooks/hello.clj"}
         :url->path {"notebooks/hello.clj" "notebooks/hello.clj"}}])

(defn index [{:as view-data :keys [paths]}]
  (set! (.-title js/document) "Clerk")
  (r/with-let [!state (r/atom {:dark-mode? (ls/get-item sci-viewer/local-storage-dark-mode-key)})
               ref-fn #(when % (sci-viewer/setup-dark-mode! !state))]
    [:div.bg-gray-100.dark:bg-gray-900.flex.justify-center.overflow-y-auto.w-screen.h-screen.p-4.md:p-0
     {:ref ref-fn}
     [:div.fixed.top-2.left-2.md:left-auto.md:right-2.z-10
      [sci-viewer/dark-mode-toggle !state]]
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
        "Generated with Clerk."]]]]))

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
    [:div.flex.h-screen.bg-white.dark:bg-gray-900
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
