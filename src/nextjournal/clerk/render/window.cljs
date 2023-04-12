(ns nextjournal.clerk.render.window
  (:require ["react" :as react]
            [applied-science.js-interop :as j]
            [nextjournal.clerk.render.hooks :as hooks]))

(defn header [{:keys [on-drag on-drag-start on-drag-end]}]
  (let [!mouse-down (hooks/use-state false)]
    (hooks/use-effect (fn []
                        (let [handle-mouse-up (fn []
                                                (on-drag-end)
                                                (reset! !mouse-down false))]
                          (js/addEventListener "mouseup" handle-mouse-up)
                          #(js/addEventListener "mouseup" handle-mouse-up))))
    (hooks/use-effect (fn []
                        (let [handle-mouse-move #(on-drag (.-movementX %) (.-movementY %))]
                          (when @!mouse-down
                            (js/addEventListener "mousemove" handle-mouse-move))
                          #(js/removeEventListener "mousemove" handle-mouse-move)))
                      [!mouse-down on-drag])
    [:div.bg-slate-100.hover:bg-slate-200.dark:bg-slate-800.dark:hover:bg-slate-700.cursor-move.w-full
     {:class "h-[14px]"
      :on-mouse-down (fn [event]
                       (on-drag-start)
                       (reset! !mouse-down {:start-x (.-screenX event) :start-y (.-screenY event)}))}]))

(defn show [& content]
  (let [!panel-ref (hooks/use-ref nil)
        !dragging? (hooks/use-state nil)]
    [:div.fixed.bg-white.dark:bg-slate-900.shadow-xl.ring-1.text-slate-800.dark:text-slate-100.hover:ring-2.rounded-lg.flex.flex-col.overflow-hidden
     {:class (str "z-[1000] " (if @!dragging? "ring-indigo-600 select-none " "ring-slate-300 dark:ring-slate-700 "))
      :ref !panel-ref
      :style {:top 30 :right 30 :width 400 :height 400}}
     [header {:on-drag (fn [dx dy]
                         (when-let [panel @!panel-ref]
                           (let [{:keys [left top]} (j/lookup (.getBoundingClientRect panel))]
                             (j/assoc-in! panel [:style :left] (str (+ left dx) "px"))
                             (j/assoc-in! panel [:style :top] (str (+ top dy) "px")))))
              :on-drag-start #(reset! !dragging? true)
              :on-drag-end #(reset! !dragging? false)}]
     (into [:div.p-3.flex-auto.overflow-auto] content)]))
