;; # 📰 Captions
(ns caption
  (:require [nextjournal.clerk :as clerk]))

;; `clerk/caption` takes arbitrary content and puts a caption underneath it. This is useful for adding captions to single images…

(clerk/caption "Figure 1: A Thermometer"
               (clerk/image "https://nextjournal.com/data/QmeyvaR3Q5XSwe14ZS6D5WBQGg1zaBaeG3SeyyuUURE2pq?filename=thermos.gif&content-type=image/gif"))

;; or any arbitrary content, like a graph:

(clerk/caption "A simple scatter plot with lines and markers"
               (clerk/plotly {:data [{:x [1 2 3 4]
                                      :y [10 15 13 17]
                                      :mode "markers"
                                      :type "scatter"}
                                     {:x [2 3 4 5]
                                      :y [16 5 11 9]
                                      :mode "lines"
                                      :type "scatter"}
                                     {:x [1 2 3 4]
                                      :y [12 9 15 12]
                                      :mode "lines+markers"
                                      :type "scatter"}]}))

;; … or even groups of images:

(clerk/caption "Implements of the Paper Printing Industry"
               (clerk/row
                (clerk/image "https://nextjournal.com/data/QmX99isUndwqBz7nj8fdG7UoDakNDSH1TZcvY2Y6NUTe6o?filename=image.gif&content-type=image/gif")
                (clerk/image "https://nextjournal.com/data/QmV8UanpZwTaLvLnKgJkR9etvVH9YPZX3rMFHN7YHbSGbv?filename=image.gif&content-type=image/gif")
                (clerk/image "https://nextjournal.com/data/QmPzBy1GBTAJf8Zzwhx5yyCfHqX5h7Wgx9geRpzgghyoEZ?filename=image.gif&content-type=image/gif")))

;; Captions can also include Markdown, e.g. to render links:

(clerk/caption
 "Modern Symmetrical Unary(7) in [Solresol](https://wiki.xxiivv.com/site/solresol.html)"
 (clerk/table {:head ["Solfège" "French IPA" "English IPA" "Meaning"]
               :rows [["Do"	"/do/" "/doʊ/" "no"]
                      ["Re" "/ʁɛ/" "/ɹeɪ/" "and, also"]
                      ["Mi" "/mi/" "/miː/" "or"]
                      ["Fa" "/fa/" "/fɑː/" "at, to"]
                      ["Sol" "/sɔl/" "/soʊl/" "but, if"]
                      ["La" "/la/" "/lɑː/" "the, then"]
                      ["Si" "/si/" "/siː/" "yes"]]}))
