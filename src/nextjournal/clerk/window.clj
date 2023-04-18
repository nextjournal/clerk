(ns nextjournal.clerk.window
  (:require [nextjournal.clerk.tap :as tap]
            [nextjournal.clerk.viewer :as v]
            [nextjournal.clerk.webserver :as webserver]))

(def taps-viewer
  {:render-fn '(fn [taps opts]
                 (into [:div]
                       (nextjournal.clerk.viewer/inspect-children opts)
                       taps))})

(defn window!
  ([id]
   (case id
     ::taps (window! id {:title "🚰 Taps" :css-class "p-0"}
                     (v/with-viewers (v/add-viewers [tap/tap-viewer])
                       (v/with-viewer taps-viewer @tap/!taps)))))
  ([id content] (window! id {} content))
  ([id opts content]
   (webserver/update-window! id (merge opts {:nextjournal/presented (update (v/present content) :nextjournal/css-class #(or % ["px-0"]))
                                             :nextjournal/hash (gensym)
                                             :nextjournal/fetch-opts {:blob-id (str id)}
                                             :nextjournal/blob-id (str id)}))))

(defn destroy-window! [id] (webserver/destroy-window! id))

(doseq [w (keys @webserver/!windows)]
  (destroy-window! w))

#_(window! ::taps)

#_(defn tapped [x] (swap! !taps conj x) (window! ::taps))
#_(defonce taps-setup (add-tap tapped))

#_(doseq [f @@(resolve 'clojure.core/tapset)] (remove-tap f))
#_(reset! !taps ())
#_(tap> (range 30))
#_(window! ::taps)
#_(destroy-window! ::taps)
#_(tap> (v/html [:h1 "Ahoi"]))
#_(tap> (v/table [[1 2] [3 4]]))
#_(window! ::my-window {:title "🔭 Rear Window"} (table [[1 2] [3 4]]))
#_(window! ::my-window {:title "🔭 Rear Window"} (range 30))
#_(window! ::my-window {:title "🔭 Rear Window"} (plotly {:data [{:y [1 2 3]}]}))
#_(window! ::my-window-2 {:title "🪟"} (range 100))
#_(destroy-window! ::my-window)
