(ns user
  (:require [clojure.string :as str]
            [nextjournal.clerk :as clerk]))

(comment
  (clerk/serve! {:browse? true}))

(comment
  ;; start without file watcher
  (clerk/serve! {})

  ;; start with file watcher
  (clerk/serve! {:watch-paths ["notebooks" "src"]})

  ;; start with file watcher and show filter function to enable notebook pinning
  (clerk/serve! {:watch-paths ["notebooks" "src"] :show-filter-fn #(clojure.string/starts-with? % "notebooks")})

  (clerk/show! "notebooks/onwards.md")
  (clerk/show! "notebooks/rule_30.clj")
  (clerk/show! "notebooks/how_clerk_works.clj")
  (clerk/show! "notebooks/pagination.clj")
  (clerk/show! "notebooks/paren_soup.clj")
  (clerk/show! "notebooks/recursive.clj")
  (clerk/show! "notebooks/tap.clj")

  (clerk/show! "notebooks/markdown.md")

  (clerk/show! "notebooks/viewer_api.clj")


  (clerk/show! "notebooks/viewers/vega.clj")
  (clerk/show! "notebooks/viewers/plotly.clj")
  (clerk/show! "notebooks/viewers/table.clj")
  (clerk/show! "notebooks/viewers/tex.clj")
  (clerk/show! "notebooks/viewers/markdown.clj")
  (clerk/show! "notebooks/viewers/html.clj")

  (clerk/show! "notebooks/sicmutils.clj")

  (clerk/show! "notebooks/dice.clj")
  (clerk/clear-cache!)
  (clerk/cache-assets! {})
  )
