;; # 🎛 Control Lab 🧑🏼‍🔬
(ns viewers.control-lab
  {:nextjournal.clerk/visibility {:code :hide :result :hide}}
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as viewer]))

;; Experimenting with ways of making controls. We start with two
;; little helper functions, one in sci and one in Clojure.

(clerk/with-viewer viewer/viewer-eval-viewer
  'viewers.control-lab/!num)


(defn make-render-slider
  ([] (make-render-slider {}))
  ([opts] (list 'partial
                '(fn [{:as opts :keys [min max step] :or {min 0 max 100 step 1}} !state]
                   (let [offset (+ 5 (* (/ (- (js/Math.min @!state max) min) (- max min)) 90))]
                     [:div.inline-flex.items-center.font-sans.pt-3
                      [:span.text-slate-500.mr-1 {:class "text-[11px] -mt-[3px]"} min]
                      [:div.relative
                       [:input (merge {:type :range
                                       :value @!state
                                       :on-input #(reset! !state (.. % -target -valueAsNumber))} opts)]
                       [:output.absolute.top-0.text-center
                        {:class "-translate-x-1/2 -translate-y-full text-[11px] min-w-[25px]"
                         :style {:left (str offset "%")}}
                        @!state]]
                      [:span.text-slate-500.ml-1 {:class "text-[11px] -mt-[3px]"} max]]))
                opts)))

(make-render-slider {:max 200 :step 0.1})

(defn slider
  ([!state] (slider {} !state))
  ([opts !state]
   (clerk/with-viewer (assoc viewer/viewer-eval-viewer :render-fn (make-render-slider opts)) !state)))


;; Let's go through the ways we can use this.

{:nextjournal.clerk/visibility {:code :show :result :show}}

;; 1️⃣ On a var coming from a def
^{::clerk/sync true ::clerk/viewer slider}
(defonce !num (atom 0))


#_(reset! !num 50)

;; 2️⃣ On a sharp quoted symbol (works with a fully qualified one as well, ofc).
(slider `!num)


;; 3️⃣ On an explicit `ViewerEval` type
(slider (viewer/->viewer-eval `!num))


#_ ;; TODO: 4️⃣ plain (not quoted) symbol
(slider !num)

;; We can customise the slider by passing different opts (that are merged).

(slider {:max 10 :step 0.002} `!num)

;; Or use a completely custom `:render-fn`.
(clerk/with-viewer
  (assoc viewer/viewer-eval-viewer
         :render-fn
         '(fn [x]
            [:div.inline-flex.items-center.bg-green-400.rounded-xl.px-2.text-xl.select-none.gap-1
             [:span.cursor-pointer {:on-click #(swap! x dec)} "⏮"]
             [:div.font-bold.font-sans.p-2.text-white.w-32.text-center @x]
             [:span.cursor-pointer {:on-click #(swap! x inc)} "⏭"]
             [:span.cursor-pointer {:on-click #(reset! x 0)} "⏹"]]))
  `!num)

;; This is the default viewer for a `ViewerEval` type.

(clerk/with-viewer viewer/viewer-eval-viewer `!num)


;; This is the value from the JVM.

@!num
