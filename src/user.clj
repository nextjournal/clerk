(ns user)

(require '[nextjournal.beholder :as beholder]
         '[nextjournal.clerk :as clerk :refer [show!]]
         '[nextjournal.clerk.view]
         '[nextjournal.clerk.webserver :as webserver])

(webserver/start! {})

(defn toggle-dev! []
  (alter-var-root #'nextjournal.clerk.view/live-js? not))

(comment
  (toggle-dev!)

  (def watcher
    (beholder/watch #(clerk/file-event %) "notebooks" "src"))

  (beholder/stop watcher)


  (show! "notebooks/elements.clj")
  (show! "notebooks/rule_30.clj")
  (show! "notebooks/onwards.clj")
  (show! "notebooks/how_clerk_works.clj")

  (show! "notebooks/viewers/vega.clj")
  (show! "notebooks/viewers/plotly.clj")
  (show! "notebooks/viewers/tex.clj")
  (show! "notebooks/viewers/markdown.clj")
  (show! "notebooks/viewers/html.clj")

  )
