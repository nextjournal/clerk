(ns nextjournal.clerk.static-app
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [nextjournal.clerk.render :as render]
            [nextjournal.clerk.sci-env :as sci-env]
            [reagent.core :as r]
            [reagent.dom.server :as dom-server]
            [reitit.frontend :as rf]
            [reitit.frontend.easy :as rfe]
            [sci.core :as sci]))

(defn doc-url [{:keys [path->url current-path bundle?]} path]
  (let [url (path->url path)]
    (if bundle?
      (str "#/" url)
      (let [url (cond-> url
                  (and (exists? js/document)
                       (= (.. js/document -location -protocol) "file:")
                       (or (nil? url)
                           (str/ends-with? url "/")))
                  (str "index.html"))
            dir-depth (get (frequencies current-path) \/ 0)
            relative-root (str/join (repeat dir-depth "../"))]
        (str relative-root url)))))

(defn show [{:keys [doc bundle?]}]
  (render/set-state! {:doc (assoc doc :bundle? bundle?)})
  [render/root])

(def routes [["/*path" {:name ::show :view show}]])

(defonce !match (r/atom nil))
(defonce !state (r/atom {}))

(defn root []
  (let [{:keys [data path-params] :as match} @!match
        {:keys [view]} data
        view-data (merge @!state data path-params {:doc (get-in @!state [:path->doc (:path path-params "")])})]
    [:div.flex.min-h-screen.bg-white.dark:bg-gray-900
     [:div.flex-auto.w-screen.scroll-container
      (if view
        [view view-data]
        [:pre (pr-str match)])]]))

(defn ^:dev/after-load mount []
  (when (and render/react-root (not render/hydrate?))
    (.render render/react-root (r/as-element [root]))))

(defn ^:export init [{:as state :keys [bundle? path->doc path->url current-path]}]
  (let [url->doc (set/rename-keys path->doc path->url)]
    (reset! !state (assoc state
                          :path->doc url->doc
                          :url->path (set/map-invert path->url)))
    (sci/alter-var-root sci-env/doc-url (constantly (partial doc-url @!state)))
    (if bundle?
      (rfe/start! (rf/router routes) #(reset! !match %1) {:use-fragment true})
      (reset! !match {:data {:view show} :path-params {:path (path->url current-path)}}))
    (mount)))

(defn ^:export ssr [state-str]
  (init (sci-env/read-string state-str))
  (dom-server/render-to-string [root]))
