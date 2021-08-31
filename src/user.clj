(ns user
  (:require [nextjournal.beholder :as beholder]
            [nextjournal.clerk :as clerk :refer [show!]]
            [nextjournal.clerk.view]
            [nextjournal.clerk.webserver :as webserver]))

(defn go []
  (def watcher
    (beholder/watch #(clerk/file-event %) "notebooks" "src"))

  (webserver/start! {}))

(defn set-dev!
  "Set this to `true` to load the css + js from a running instance for css + viewer dev. "
  [enabled?]
  (alter-var-root #'nextjournal.clerk.view/live-js? (constantly enabled?)))

(comment
  (go)

  nextjournal.clerk.view/live-js?

  (set-dev! true)

  (beholder/stop watcher)

  (show! "notebooks/onwards.clj")
  (show! "notebooks/elements.clj")
  (show! "notebooks/rule_30.clj")
  (show! "notebooks/onwards.clj")
  (show! "notebooks/how_clerk_works.clj")
  (show! "notebooks/pagination.clj")
  (show! "notebooks/recursive.clj")
  (show! "notebooks/cache.clj")

  (show! "notebooks/viewers/vega.clj")
  (show! "notebooks/viewers/plotly.clj")
  (show! "notebooks/viewers/tex.clj")
  (show! "notebooks/viewers/markdown.clj")
  (show! "notebooks/viewers/html.clj")

  (show! "/Users/mk/dev/ductile/repl_sessions/edifact_bucket_analysis.clj")

  (clerk/clear-cache!)

  )
