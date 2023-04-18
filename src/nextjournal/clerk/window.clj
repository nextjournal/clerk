(ns nextjournal.clerk.window
  (:require [nextjournal.clerk.tap :as tap]
            [nextjournal.clerk.viewer :as v]
            [nextjournal.clerk.webserver :as webserver]))

(declare open!)
(defonce !taps-view (atom :stream))
(defn set-view! [x] (reset! !taps-view x) (open! ::taps))

(def taps-viewer
  {:render-fn '(fn [taps {:as opts :keys [taps-view]}]
                 [:div.flex.flex-col
                  [:div.flex.justify-between.items-center.font-sans.border-b.border-t.shadow.z-1
                   {:class "text-[11px] height-[24px] px-[8px]"}
                   (into [:div.flex.items-center]
                         (map (fn [choice]
                                [:button.transition-all.mr-2.relative
                                 {:class (str "h-[24px] "
                                              (if (= taps-view choice)
                                                "text-indigo-600 font-bold "
                                                "text-slate-500 hover:text-indigo-600 "))
                                  :on-click #(nextjournal.clerk.render/clerk-eval `(set-view! ~choice))}
                                 (clojure.string/capitalize (name choice))])
                              [:stream :latest]))
                   [:button.text-slate-500.hover:text-indigo-600
                    {:on-click #(nextjournal.clerk.render/clerk-eval `(tap/reset-taps!))}
                    "Clear"]]
                  (into [:div.overflow-auto
                         {:style {:height "calc(100% - 40px)"}}]
                        (nextjournal.clerk.viewer/inspect-children opts)
                        (cond->> taps (= :latest taps-view) (take 1)))])})

(defn open!
  ([id]
   (case id
     ::taps (open! id {:title "🚰 Taps" :css-class "p-0 relative overflow-auto"}
                   (v/with-viewers (v/add-viewers [tap/tap-viewer])
                     (v/with-viewer taps-viewer {:nextjournal/opts {:taps-view @!taps-view}}
                       @tap/!taps)))))
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
#_(tap> (range 30))
#_(destroy! ::taps)
#_(tap> (v/plotly {:data [{:y [1 2 3]}]}))
#_(tap> (v/table [[1 2] [3 4]]))
#_(open! ::my-window {:title "🔭 Rear Window"} (v/table [[1 2] [3 4]]))
#_(open! ::my-window {:title "🔭 Rear Window"} (range 30))
#_(open! ::my-window {:title "🔭 Rear Window"} (v/plotly {:data [{:y [1 2 3]}]}))
#_(open! ::my-window-2 {:title "🪟"} (range 100))
#_(destroy! ::my-window)
#_(destroy-all!)
