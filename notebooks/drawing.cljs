(ns drawing
  (:require [nextjournal.clerk.sci-viewer :as v]
            [applied-science.js-interop :as j]
            [goog.functions :as gfns]
            [reagent.core :as r]))

(defn freehand [{:as opts :keys [store-at svg]}]
  (let [d (gfns/throttle #(do (js/console.log (str "ahoi:" %)) %) 300)]
    (js/console.log "Freehand" store-at (count svg)
                    "dbn" (do (d 1) (d 2) (d 3))))
  (v/html
   [v/with-d3-require {:package "two.js@0.7.13"}
    (fn [Two]
      (defonce ^js drawing (Two. (j/obj :autostart true
                                    :fitted true
                                    :type (.. Two -Types -svg))))
      ;; always clerk on clerk show!
      (.clear drawing)
      (when svg
        (.add drawing (.load drawing svg)))
      (r/with-let
       [state (atom {:drawing drawing})
        Anchor (.-Anchor Two)  Path (.-Path Two)
        add-point! (gfns/throttle (fn [curve x y]
                            (js/console.log "adding" x y )
                            (.. curve -vertices (push (Anchor. x y))))
                          50)
        mouse-move (fn [e]
                     (js/requestAnimationFrame
                      #(let [bcr (:bcr @state)
                            x (- (.-clientX e) (.-x bcr))
                            y (- (.-clientY e) (.-y bcr))]
                        (when-some [c (:curve @state)]
                          (add-point! c x y)))))

        build-curve (fn []
                      (let [curve (doto (Path.)
                                    (j/assoc! :curved true)
                                    (j/assoc! :closed false)
                                    (j/assoc! :stroke "#f97316")
                                    (j/assoc! :linewidth 10)
                                    (j/assoc! :cap "round")
                                    (j/assoc! :opacity 0.75)
                                    .noFill)]
                        (.add drawing curve)
                        curve))


        save (fn []
               (let [svg (.. (js/XMLSerializer.) (serializeToString (.. drawing -renderer -domElement)))]
                 (js/console.log "store-at:" store-at )
                 (v/clerk-eval (list 'spit store-at svg)))
               )
        refn (fn [elm]
               (when-not elm (js/console.log "unmount" ))
               (when elm
                 (.appendTo drawing elm)
                 (swap! state assoc :bcr (.getBoundingClientRect elm))
                 (.addEventListener (js/document.querySelector ".flex-auto.h-screen.overflow-y-auto")
                                    "scroll"
                                    #(js/requestAnimationFrame
                                      (fn [_] (swap! state assoc :bcr (.getBoundingClientRect elm)))))
                 (.addEventListener elm "mousedown" #(swap! state assoc :curve (build-curve)))
                 (.addEventListener elm "mouseup" #(swap! state dissoc :curve))
                 (.addEventListener elm "mousemove" mouse-move)))]
       [:div.flex.items-left
        [:button.flex.cursor-pointer.border-sky-100 {:on-click save} "⏺"]
        [:div.border {:ref refn :style {:width "100%" :height "800px"}}]]))]))
