;; # 🚰 Tap Inspector
^{:nextjournal.clerk/visibility :hide}
(ns nextjournal.clerk.tap
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as v]
            [clojure.core :as core]))

^{::clerk/viewer {:transform-fn (fn [{::clerk/keys [var-from-def]}]
                                  {:var-name (symbol var-from-def) :value @@var-from-def})
                  :fetch-fn (fn [_ x] x)
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
                                    [:button.text-xs.rounded-full.px-3.py-1.border-2.font-sans.hover:bg-slate-100.cursor-pointer {:on-click #(v/clerk-eval `(reset-taps!))} "Clear"]])))}}
(defonce !view (atom :stream))


^{::clerk/viewer clerk/hide-result}
(defonce !taps (atom []))

^{::clerk/viewer clerk/hide-result}
(defn reset-taps! []
  (reset! !taps []))

#_(reset-taps!)

^{::clerk/viewer clerk/hide-result}
(defn fetch-tap [{:as opts :keys [describe-fn path offset trace-fn]} x]
  (when trace-fn (trace-fn {:origin 'fetch-tap :xs x}))
  (let [path' (cond-> path
                (not= :tap (peek path)) (conj :tap))
        opts (cond-> opts (v/viewers (:tap x)) (update :viewers #(concat (v/viewers (:tap x)) %)))]
    (-> (cond-> (update x :tap describe-fn (assoc opts :!budget (atom 100) :path path') path')
          (-> path count dec pos?) :tap)
        (assoc :path path' :replace-path (conj path offset)))))

^{::clerk/viewer clerk/hide-result}
(def taps-viewer {:render-fn '(fn [taps opts]
                                (v/html [:div.flex.flex-col.pt-2
                                         (map (fn [tap] (let [{:keys [tap inst key]} (:nextjournal/value tap)]
                                                         (with-meta 
                                                           [:div.border-t.relative.py-3
                                                            [:span.absolute.rounded-full.px-2.bg-gray-300.font-mono.top-0
                                                             {:class "left-1/2 -translate-x-1/2 -translate-y-1/2 py-[1px] text-[9px]"}
                                                             (.toLocaleTimeString inst "en-US" (clj->js {:hour12 false})) "."
                                                             (subs (str (+ 1000 (.getMilliseconds inst))) 1)]
                                                            [:div.overflow-x-auto [v/inspect tap]]]
                                                           {:key key})))
                                              taps)]))
                  :transform-fn (fn [taps]
                                  (mapv (partial clerk/with-viewer {:fetch-fn fetch-tap}) (reverse taps)))})


^{::clerk/viewer (if (= :latest @!view)
                   (update taps-viewer :transform-fn (fn [orig-fn] (fn [xs] (orig-fn [(peek xs)]))))
                   taps-viewer)}
@!taps




^{::clerk/viewer clerk/hide-result}
(defn tapped [x]
  (swap! !taps conj {:tap x :inst (java.time.Instant/now) :key (str (gensym))})
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
  (defn process-trace [trace]
    (-> trace
        (select-keys [:origin :xs :current-path :path :descend?])))

  (let [!trace (atom [])]
    (nextjournal.clerk.viewer/describe
     (clerk/with-viewer taps-viewer
       [{:tap (javax.imageio.ImageIO/read (java.net.URL. "file:/Users/mk/Desktop/CleanShot 2022-03-28 at 15.15.15@2x.png"))}])
     {:trace-fn #(swap! !trace conj (process-trace %)) :path [0]})
    (clerk/code @!trace))


  (let [!trace (atom [])]
    (nextjournal.clerk.viewer/describe
     (clerk/table [[(javax.imageio.ImageIO/read (java.net.URL. "file:/Users/mk/Desktop/CleanShot 2022-03-28 at 15.15.15@2x.png"))]])
     {:trace-fn #(swap! !trace conj (process-trace %)) :path [0 0]})
    (clerk/code @!trace)))


^{::clerk/viewer clerk/hide-result}
(comment
  (do (tap> (rand-int 1000))
      (tap> (rand-int 1000)))
  (tap> (shuffle (range (+ 20 (rand-int 200)))))
  (tap> (clerk/md "> The purpose of visualization is **insight**, not pictures."))
  (tap> (v/plotly {:data [{:z [[1 2 3]
                               [3 2 1]]
                           :type "surface"}]}))
  (tap> (javax.imageio.ImageIO/read (java.net.URL. "https://images.freeimages.com/images/large-previews/773/koldalen-4-1384902.jpg")))

  (do (require 'rule-30)
      (tap> (clerk/with-viewers rule-30/viewers rule-30/rule-30))
      (tap> (clerk/with-viewers rule-30/viewers rule-30/board)))

  (tap> (clerk/html [:h1 "Fin. 👋"]))
  )
