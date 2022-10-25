;; # 🗂 Open Graph Metadata
(ns open-graph
  {:nextjournal.clerk/open-graph
   {:title "My title"
    :url "https://clerk.vision"
    :image "https://url-to-image"}}
  (:require [nextjournal.clerk :as clerk])
  (:import (javax.imageio ImageIO)
           (java.net URL)))

;; ## Proposal
;; Clerk static page generator need to produce valid [Open Graph](https://ogp.me) metadata.

;; A Specific result may be specified via metadata
^{::clerk/open-graph {:image :result}}
(clerk/col
 (clerk/html [:h1 "This is my Card"]))

;; By default the last result will serve the purpose.
(ImageIO/read (URL. "https://etc.usf.edu/clipart/186600/186669/186669-trees-in-the-winter_sm.gif"))

(defn render-open-graph-metadata [{:as doc :keys [open-graph]}]
  (into [:<>]
        (keep (fn [[prop content]]
                (when (string? content)
                  [:meta {:property (str "og" prop) :content content}])))
        open-graph))

(clerk/code
 (some-> @nextjournal.clerk.webserver/!doc
         render-open-graph-metadata))
