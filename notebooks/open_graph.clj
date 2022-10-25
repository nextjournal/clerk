;; # 🗂 Open Graph Metadata
(ns open-graph
  {:nextjournal.clerk/open-graph
   {:url "https://clerk.vision"
    :image "https://url-to-image"}}
  (:require [nextjournal.clerk :as clerk]
            [clojure.java.shell :as shell]
            [babashka.fs :as fs])
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

(def og-card-preview
  {:transform-fn (comp clerk/mark-presented (clerk/update-val :open-graph))
   :render-fn
   '(fn [{:as open-graph :keys [title description image]}]
      [:div.flex.flex-col.items-center
       [:div.border.border-gray-200 {:style {:width "10rem"}}
        [:h2 title]
        [:img {:src "build/foo/bar.png"}]
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
  @nextjournal.clerk.webserver/!doc
  #_ #_ use-this-if-the-above-hangs-show
  {:open-graph {:type "article:clerk",
                :title "My title",
                :description "Clerk static page generator need to produce valid Open Graph metadata.",
                :url "https://clerk.vision",
                :image "https://url-to-image"}})

^{:nextjournal.clerk/visibility {:code :hide :result :hide}}
(comment

  (do
    (fs/delete-tree "ui_tests/screenshots")
    (shell/sh "yarn" "nbb" "-m" "screenshots"
              "--url" "localhost:7777"
              "--out-dir" "screenshots"
              :dir "ui_tests")
    (as-> (fs/path "ui_tests/screenshots") dir
      (when (fs/exists? dir)
        (fs/list-dir dir))))

  )
