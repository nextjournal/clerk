;; # 🗂 Open Graph Metadata
(ns open-graph
  {:nextjournal.clerk/no-cache true
   :nextjournal.clerk/open-graph
   {:url "https://clerk.vision"
    #_#_
    :image "https://cdn.nextjournal.com/data/QmSucfUyXCMKg1QbgR3QmLEWiRJ9RJvPum5GqjLPsAyngx?filename=clerk-eye.png&content-type=image/png"}}
  (:require [nextjournal.clerk :as clerk]
            [clojure.string :as str]
            [clojure.java.shell :as shell]
            [babashka.fs :as fs]
            [nextjournal.clerk.viewer :as viewer]
            [clojure.string :as str])
  (:import (javax.imageio ImageIO)
           (java.net URL)))

;; ## Proposal
;; Clerk static page generator need to produce valid [Open Graph](https://ogp.me) metadata.

;; A Specific result may be specified via metadata
^{::clerk/open-graph {:image :result}}
(clerk/col
 (clerk/html [:h1.pt-10 {:style {:font-size "3rem"}} "Stats"])
 (clerk/plotly {:layout {:margin {:l 20 :r 0 :b 20 :t 0}
                         :autosize false :width 300 :height 200}
                :config {:displayModeBar false :displayLogo false}
                :data [{:x ["giraffes" "orangutans" "monkeys"]
                        :y [20 14 23]
                        :type "bar"}]}))

;; By default the last result will serve the purpose.
(ImageIO/read (URL. "https://etc.usf.edu/clipart/186600/186669/186669-trees-in-the-winter_sm.gif"))

(def screenshots-dir "public/build/_data/screenshots")

(defn take-screenshots! []
  (fs/delete-tree screenshots-dir)
  (shell/sh "yarn" "nbb" "-m" "screenshots" "--url" "localhost:7777" "--out-dir" (str "../" screenshots-dir)
            :dir "ui_tests"))

(defn add-screenshots [{:as og :keys [image]}]
  (cond-> og
    (and (not image) (fs/exists? screenshots-dir))
    (assoc :image
           (str "http://localhost:7777/build/_data/screenshots/"
                (or (some #(and (str/starts-with? % "result") %)
                          (map fs/file-name (fs/list-dir screenshots-dir)))
                    "page.png")))))

(def og-card-preview
  {:transform-fn (comp clerk/mark-presented (clerk/update-val (comp add-screenshots :open-graph)))
   :render-fn
   '(fn [{:as open-graph :keys [title description image]}]
      [:div.flex.flex-col.items-center.m-20
       [:div.border.border-gray-200.mb-10 {:style {:width "20rem"}}
        [:h2 title]
        [:img {:src image}]
        [:div description]]
       [:div.viewer-code.border.border-grey-200
        [nextjournal.clerk.render/inspect
         (v/code
          (into [:<>]
                (keep (fn [[prop content]]
                        (when (string? content)
                          [:meta {:property (str "og" prop) :content content}])))
                open-graph))]]])})

(clerk/with-viewer og-card-preview
  ;; FIXME: can't use this here
  #_ @nextjournal.clerk.webserver/!doc
  {:open-graph {:type "article:clerk",
                :title "My title",
                :description "Clerk static page generator need to produce valid Open Graph metadata.",
                :url "https://clerk.vision"}})

(defn take-screenshots-and-preview! []
  (take-screenshots!)
  (clerk/add-viewers! [(assoc og-card-preview :name :clerk/notebook)])
  (clerk/recompute!))

(defn reset-notebook! []
  (clerk/reset-viewers! viewer/default-viewers)
  (clerk/recompute!))

^{:nextjournal.clerk/visibility {:code :hide :result :hide}}
(comment
  ;; take screenshots of the current shown doc
  (take-screenshots-and-preview!)
  ;; reset view
  (reset-notebook!)

  (take 20 (.getBytes (slurp "public/build/_data/screenshots/page.png")))
  )
