(ns nextjournal.clerk.experimental
  "A bucket of Clerk experiments.

  Anything in here may change or go away at any time, use with caution."
  (:require [nextjournal.clerk.viewer :as viewer]))


(defn render-slider
  ([] (render-slider {}))
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

(defn slider
  "Creates a slider control from a `!state` atom.

  Takes an optional first map argument with the valid keys (and defaults):
  `:min` minimum value (0)
  `:max` maximum value (100)
  `:step` increment (1)"
  ([!state] (slider {} !state))
  ([opts !state] (viewer/with-viewer (assoc viewer/viewer-eval-viewer :render-fn (render-slider opts)) !state)))


(defn render-text-input
  ([] (render-slider {}))
  ([opts] (list 'partial
                '(fn [{:as opts :keys [placeholder] :or {placeholder "⌨️"}} !state]
                   [:input {:type :text
                            :placeholder "⌨️"
                            :value @!state
                            :class "px-3 py-3 placeholder-blueGray-300 text-blueGray-600 relative bg-white bg-white rounded text-sm border border-blueGray-300 outline-none focus:outline-none focus:ring w-full"
                            :on-input #(reset! !state (.. % -target -value))}])
                opts)))

(defn text-input
  ([!state] (text-input {} !state))
  ([opts !state] (viewer/with-viewer (assoc viewer/viewer-eval-viewer :render-fn (render-text-input opts)) !state)))

