;; # Vega Callback + Atom sync example
(ns viewers.vega-callback-with-atom-sync
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as v]))


;; Clerk will create a corresponding (reagent) atom in the browser for atoms
;; tagged with `^::clerk/sync`

^::clerk/sync
(defonce syncstate (atom nil))

;; a vega-lite bar chart where you can select an interval on the chart.
;; Selection events are captured and used to update a synce atom
(clerk/vl
  {:width 450
   :height 200
   :encoding {"x" {"field" "letters" "type" "nominal" "axis" {"labelAngle" 0}}
              "y" {"field" "b" "type" "quantitative"}}
   :layer [;; main bar chart
           {:data {:values [{"letters" "A" "b" 28}
                            {"letters" "B" "b" 100}
                            {"letters" "C" "b" 43}
                            {"letters" "D" "b" 91}
                            {"letters" "E" "b" 81}
                            {"letters" "F" "b" 53}
                            {"letters" "G" "b" 19}
                            {"letters" "H" "b" 87}
                            {"letters" "I" "b" 52}]}
            :mark "bar"}
           ;; the interval selector
           {:params [{:name "brush"
                      :select {:type "interval"
                               :encodings ["x"]}}]
            :mark "area"}]
   ;; call this when the embedded vega-lite object is created
   :embed/callback (v/->viewer-eval
                     '(fn [embedded-vega]
                        (let [view (.-view embedded-vega)
                              !selection-state (atom nil)]
                          ;; on every selection change, store the selection
                          (.addSignalListener view "brush" (fn [_signal selection]
                                                             (reset! !selection-state
                                                                     (js->clj (.-letters selection)))))
                          ;; mouse releases set the sync'd atom to the current
                          ;; selection, avoiding many updates to sync'd atom on
                          ;; every intermediate selection change
                          (.addEventListener view "mouseup" (fn [_event _item]
                                                              (swap! viewers.vega-callback-with-atom-sync/syncstate
                                                                     (constantly (deref !selection-state))))))
                        embedded-vega))
   :embed/opts {:actions false}})

;; show the atom that was updated by selecting an interval on the graph on the
;; browser side
@syncstate
