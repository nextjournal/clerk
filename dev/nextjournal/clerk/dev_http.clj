(ns nextjournal.clerk.dev-http
  (:require [nextjournal.clerk.view :as view]
            [hiccup.page :as hiccup]))


(defn devcards-html []
  (hiccup/html5
   {:class "overflow-hidden min-h-screen"}
   [:head
    [:title "Clerk Devcards"]
    [:meta {:charset "UTF-8"}]
    [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]    
    (view/include-css+js)]
   [:body.dark:bg-gray-900
    [:div#app]
    [:script "nextjournal.devcards.main.init();"]]))

(defn devcards-handler [{:as req :keys [uri]}]
  (cond (= "/" uri) {:status 200 :body (devcards-html)}
        :else {:status 404}))
