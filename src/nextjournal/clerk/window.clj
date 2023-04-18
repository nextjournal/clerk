(ns nextjournal.clerk.window
  (:require [nextjournal.clerk.tap :as tap]
            [nextjournal.clerk.viewer :as v]
            [nextjournal.clerk.webserver :as webserver]))

(def taps-viewer
  {:render-fn '(fn [taps opts]
                 (let [!view (nextjournal.clerk.render.hooks/use-state :stream)]
                   [:div
                    [:div.flex.justify-between.items-center
                     (into [:div.flex.items-center.font-sans.text-xs.mb-3 [:span.text-slate-500.mr-2 "View-as:"]]
                           (map (fn [choice]
                                  [:button.px-3.py-1.font-medium.hover:bg-indigo-50.rounded-full.hover:text-indigo-600.transition
                                   {:class (if (= @!view choice) "bg-indigo-100 text-indigo-600" "text-slate-500")
                                    :on-click #(reset! !view choice)}
                                   choice]) [:stream :latest]))
                     [:button.text-xs.rounded-full.px-3.py-1.border-2.font-sans.hover:bg-slate-100.cursor-pointer
                      {:on-click #(nextjournal.clerk.render/clerk-eval `(reset-taps!))} "Clear"]]
                    (into [:div]
                          (nextjournal.clerk.viewer/inspect-children opts)
                          (cond->> taps (= :latest @!view) (take 1)))]))})

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
