;; # 🚰 Tap Inspector
^{:nextjournal.clerk/visibility :hide}
(ns nextjournal.clerk.tap
  (:require [clojure.core :as core]
            [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as v])
  (:import (java.time Instant LocalTime ZoneId)))

^{::clerk/viewer clerk/hide-result}
(def switch-view
  {:transform-fn (comp clerk/assoc-reduced
                       (clerk/update-value (fn [{::clerk/keys [var-from-def]}]
                                             {:var-name (symbol var-from-def) :value @@var-from-def})))
   :render-fn '(fn [{:keys [var-name value]}]
                 (v/html
                  (let [choices [:stream :latest]]
                    [:div.flex.justify-between.items-center
                     (into [:div.flex.items-center.font-sans.text-xs.mb-3 [:span.text-slate-500.mr-2 "View-as:"]]
                           (map (fn [choice]
                                  [:button.px-3.py-1.font-medium.hover:bg-indigo-50.rounded-full.hover:text-indigo-600.transition
                                   {:class (if (= value choice) "bg-indigo-100 text-indigo-600" "text-slate-500")
                                    :on-click #(v/clerk-eval `(reset! ~var-name ~choice))}
                                   choice]) choices))
                     [:button.text-xs.rounded-full.px-3.py-1.border-2.font-sans.hover:bg-slate-100.cursor-pointer {:on-click #(v/clerk-eval `(reset-taps!))} "Clear"]])))})

^{::clerk/viewer switch-view}
(defonce !view (atom :stream))


^{::clerk/viewer clerk/hide-result}
(defonce !taps (atom []))

^{::clerk/viewer clerk/hide-result}
(defn reset-taps! []
  (reset! !taps []))

#_(reset-taps!)

^{::clerk/viewer clerk/hide-result}
(defn inst->local-time-str [inst]
  (str (LocalTime/ofInstant inst (ZoneId/systemDefault))))

#_(inst->local-time-str (Instant/now))

^{::clerk/viewer clerk/hide-result}
(defn describe-only-key [key {:as wrapped-value :keys [offset]}]
  (-> (update wrapped-value :nextjournal/value (fn [x]
                                                 (when (empty? (:path wrapped-value))
                                                   (throw (ex-info "path cannot be empty?" {:path (:path wrapped-value) :wrapped-value wrapped-value})))
                                                 (cond-> (update x key v/describe (-> wrapped-value
                                                                                      v/->opts
                                                                                      (update :path conj key)
                                                                                      (update :current-path conj key)
                                                                                      (assoc :budget 100000)))
                                                   (pos-int? offset) key)))
      v/assoc-reduced))

^{::clerk/viewer clerk/hide-result}
(def tap-viewer
  {:name :tapped-value
   :render-fn '(fn [tap opts]
                 (let [{:keys [tap tapped-at key]} tap]
                   (v/html (with-meta
                             [:div.border-t.relative.py-3
                              [:span.absolute.rounded-full.px-2.bg-gray-300.font-mono.top-0
                               {:class "left-1/2 -translate-x-1/2 -translate-y-1/2 py-[1px] text-[9px]"} tapped-at]
                              [:div.overflow-x-auto [v/inspect tap]]]
                             {:key key}))))
   :transform-fn (comp (partial describe-only-key :tap)
                       (clerk/update-value #(update % :tapped-at inst->local-time-str)))})

^{::clerk/viewer clerk/hide-result}
(clerk/add-viewers! [tap-viewer])

^{::clerk/viewer clerk/hide-result}
(def taps-viewer
  {:render-fn '#(v/html (into [:div.flex.flex-col.pt-2] (v/inspect-children %2) %1))
   :transform-fn (clerk/update-value (fn [taps]
                                       (mapv (partial clerk/with-viewer :tapped-value) (reverse taps))))})

^{::clerk/viewer (if (= :latest @!view)
                   {:transform-fn (clerk/update-value (comp (partial clerk/with-viewer tap-viewer) peek))}
                   taps-viewer)}
@!taps

^{::clerk/viewer clerk/hide-result}
(defn tapped [x]
  (swap! !taps conj {:tap x :tapped-at (java.time.Instant/now) :key (str (gensym))})
  (binding [*ns* (find-ns 'tap)]
    (clerk/recompute!)))

#_(tapped (rand-int 1000))

#_(reset! @(find-var 'clojure.core/tapset) #{})

^{::clerk/viewer clerk/hide-result}
(defonce setup
  (add-tap tapped))

#_(remove-tap tapped)

^{::clerk/viewer clerk/hide-result}
(comment
  (dotimes [i 5] 
    (tap> (rand-int 1000)))
  (tap> (shuffle (range (+ 20 (rand-int 200)))))
  (tap> (clerk/md "> The purpose of visualization is **insight**, not pictures."))
  (tap> (v/plotly {:data [{:z [[1 2 3]
                               [3 2 1]]
                           :type "surface"}]}))
  (tap> (javax.imageio.ImageIO/read (java.net.URL. "https://images.freeimages.com/images/large-previews/773/koldalen-4-1384902.jpg")))

  (do (require 'rule-30)
      (tap> (clerk/with-viewers rule-30/viewers rule-30/rule-30)))
  
  (tap> (clerk/with-viewers rule-30/viewers rule-30/board))

  (tap> (clerk/html [:h1 "Fin. 👋"]))

  (reset! !taps [])
  ;; ---
  ;; ## TODO

  ;; * [x] Avoid flickering when adding new tap
  ;; * [x] Record & show time of tap
  ;; * [x] Keep expanded state when adding tap
  ;; * [x] Fix latest
  ;; * [ ] Fix lazy loading
  ;; * [ ] Improve performance when large image present in tap stream
  
  )
