(ns user
  (:require [nextjournal.clerk :as clerk]))

(defn go []
  (clerk/serve! {}))

(defn set-dev!
  "Set this to `true` to load the css + js from a running instance for css + viewer dev. "
  [enabled?]
  (alter-var-root #'nextjournal.clerk.view/live-js? (constantly enabled?)))

(comment
  ;; start without file watcher
  (clerk/serve! {})

  ;; start with file watcher
  (clerk/serve! {:watch-paths ["notebooks" "src"]})

  ;; start with file watcher and show filter function to enable notebook pinning
  (clerk/serve! {:watch-paths ["notebooks" "src"] :show-filter-fn #(clojure.string/starts-with? % "notebooks")})

  nextjournal.clerk.view/live-js?

  (set-dev! false)
  (set-dev! true)

  (beholder/stop watcher)

  (clerk/show! "notebooks/onwards.clj")
  (clerk/show! "notebooks/elements.clj")
  (clerk/show! "notebooks/rule_30.clj")
  (clerk/show! "notebooks/how_clerk_works.clj")
  (clerk/show! "notebooks/pagination.clj")
  (clerk/show! "notebooks/paren_soup.clj")
  (clerk/show! "notebooks/recursive.clj")

  (clerk/show! "notebooks/viewer_api.clj")

  (clerk/show! "notebooks/viewers/vega.clj")
  (clerk/show! "notebooks/viewers/plotly.clj")
  (clerk/show! "notebooks/viewers/tex.clj")
  (clerk/show! "notebooks/viewers/markdown.clj")
  (clerk/show! "notebooks/viewers/html.clj")

  (clerk/clear-cache!)

  )
