(ns nextjournal.clerk.window
  (:require [nextjournal.clerk.tap :as tap]
            [nextjournal.clerk.viewer :as v]
            [nextjournal.clerk.webserver :as webserver]))

(def taps-viewer
  {:render-fn '(fn [taps opts]
                 (into [:div]
                       (nextjournal.clerk.viewer/inspect-children opts)
                       taps))})

(defn open!
  ([id]
   (case id
     ::taps (open! id {:title "🚰 Taps" :css-class "p-0"}
                   (v/with-viewers (v/add-viewers [tap/tap-viewer])
                       (v/with-viewer taps-viewer @tap/!taps)))))
  ([id content] (open! id {} content))
  ([id opts content]
   ;; TODO: consider calling v/transform-result
   (webserver/update-window! id (merge opts {:nextjournal/presented (update (v/present content) :nextjournal/css-class #(or % ["px-0"]))
                                             :nextjournal/hash (gensym)
                                             :nextjournal/fetch-opts {:blob-id (str id)}
                                             :nextjournal/blob-id (str id)}))))

(add-watch tap/!taps ::tap-watcher (fn [_ _ _ _] (open! ::taps)))

(defn destroy! [id] (webserver/destroy-window! id))

(defn destroy-all! []
  (doseq [w (keys @webserver/!windows)]
    (destroy! w)))

#_(open! ::taps)
#_(doseq [f @@(resolve 'clojure.core/tapset)] (remove-tap f))
#_(reset! !taps ())
#_(tap> (range 30))
#_(destroy! ::taps)
#_(tap> (v/html [:h1 "Ahoi"]))
#_(tap> (v/table [[1 2] [3 4]]))
#_(open! ::my-window {:title "🔭 Rear Window"} (v/table [[1 2] [3 4]]))
#_(open! ::my-window {:title "🔭 Rear Window"} (range 30))
#_(open! ::my-window {:title "🔭 Rear Window"} (v/plotly {:data [{:y [1 2 3]}]}))
#_(open! ::my-window-2 {:title "🪟"} (range 100))
#_(destroy! ::my-window)
#_(destroy-all!)
