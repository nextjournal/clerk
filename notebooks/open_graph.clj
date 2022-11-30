;; # 🗂 Open Graph Metadata
(ns open-graph
  {:nextjournal.clerk/no-cache true
   :nextjournal.clerk/open-graph
   {:url "https://clerk.vision"
    :title "🔫 So OG"}}
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [clojure.java.shell :as shell]
            [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as viewer]
            [nextjournal.clerk.webserver :as webserver])
  (:import (javax.imageio ImageIO)
           (java.net URL)))

;; ## Proposal
;; The first paragraph of the notebook should be used as description in open graph meta.
;;
;; For context see [Open Graph](https://ogp.me) metadata.

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
(ImageIO/read (URL. "https://nextjournal.com/data/QmS7YdXsuN8Db5frzdny9XxUsDmTV7rq5fUG9XMoePWY5y?filename=trees.png&content-type=image/png"))

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

(def open-graph-card-preview
  {:transform-fn (comp clerk/mark-presented
                       #(assoc % :nextjournal/width :full)
                       (clerk/update-val (comp add-screenshots :open-graph)))
   :render-fn
   '(fn [{:as open-graph :keys [title description image]}]
      [:div.flex.flex-col.items-center.m-20
       [:div.border.border-gray-200.mb-10
        [:h2 title]
        [:img {:src image}]
        [:div description]]
       [:div.viewer-code.overflow-scroll
        [nextjournal.clerk.render/inspect
         (v/code (v/open-graph-metas open-graph))]]])})

(clerk/with-viewer open-graph-card-preview
  ;; FIXME: can't use this here
  #_ @nextjournal.clerk.webserver/!doc
  {:open-graph {:type "article:clerk",
                :title "My title",
                :description "Some description"
                :image "https://cdn.nextjournal.com/data/QmSucfUyXCMKg1QbgR3QmLEWiRJ9RJvPum5GqjLPsAyngx?filename=clerk-eye.png&content-type=image/png"
                :url "https://clerk.vision"}})

(defn preview-open-graph-card! [& {:keys [screenshots?] :or {screenshots? false}}]
  (when screenshots? (take-screenshots!))
  (viewer/reset-viewers! (or (:ns @webserver/!doc) *ns*)
                         (viewer/add-viewers [(assoc open-graph-card-preview :name :clerk/notebook)]))
  (clerk/recompute!))

(defn reset-notebook! []
  (viewer/reset-viewers! (or (:ns @webserver/!doc) *ns*) viewer/default-viewers)
  (clerk/recompute!))

^{:nextjournal.clerk/visibility {:code :hide :result :hide}}
(comment
  ;; show! any notebook, then evaluate the following to preview Open Graph stuff
  ;; take screenshots of the current shown doc
  (preview-open-graph-card!)
  (preview-open-graph-card! :screenshots? true)
  ;; reset view
  (reset-notebook!)

  (:open-graph @webserver/!doc))
