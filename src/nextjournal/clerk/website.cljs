(ns nextjournal.clerk.website
  (:require [applied-science.js-interop :as j]
            [clojure.core :as core]
            [goog.object]
            [nextjournal.devcards :as dc]
            [nextjournal.devcards.routes :as devcards-routes]
            [nextjournal.viewer.code :as code]
            [nextjournal.viewer.katex :as katex]
            [nextjournal.viewer.markdown :as markdown]
            [nextjournal.viewer.mathjax :as mathjax]
            [nextjournal.viewer.plotly :as plotly]
            [nextjournal.viewer.vega-lite :as vega-lite]
            [nextjournal.view.context :as context]
            [react :as react]
            [reagent.core :as r]
            [reagent.ratom :as ratom]
            [reagent.dom :as rdom]
            [re-frame.context :as rf]
            [clojure.string :as str]
            [sci.core :as sci]
            [sci.impl.vars]))

(dc/defcard website
  [:div.sans-serif.pb-8.bg-black
   [:div.bg-black.bg-no-repeat
    {:style {:height 300
             :background-size "100%"
             :background-image "url(/images/clerk.png)"
             :background-position "center center"}}]
   [:div.mx-auto.px-8.mt-8.text-gray-100
    [:h1.text-2xl.font-bold.text-center
     "Local-first Notebooks for Clojure"]
    [:p.mt-3.text-lg.text-center
     "Clerk takes a Clojure namespace and turns it into a notebook."]
    [:img {:src "/images/clerk-notebook.png"}]]])
