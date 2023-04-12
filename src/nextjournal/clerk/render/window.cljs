(ns nextjournal.clerk.render.window
  (:require ["react" :as react]
            [applied-science.js-interop :as j]
            [nextjournal.clerk.render.hooks :as hooks]))

(defn resizer [{:keys [on-resize on-resize-start on-resize-end] :or {on-resize-start #() on-resize-end #()}}]
  (let [!direction (hooks/use-state nil)
        !mouse-down (hooks/use-state false)
        handle-mouse-down (fn [dir]
                            (on-resize-start)
                            (reset! !direction dir)
                            (reset! !mouse-down true))]
    (hooks/use-effect (fn []
                        (let [handle-mouse-move (fn [e]
                                                  (when-let [dir @!direction]
                                                    (on-resize dir (.-movementX e) (.-movementY e))))]
                          (when @!mouse-down
                            (js/addEventListener "mousemove" handle-mouse-move))
                          #(js/removeEventListener "mousemove" handle-mouse-move)))
                      [!mouse-down !direction on-resize])
    (hooks/use-effect (fn []
                        (let [handle-mouse-up (fn []
                                                (on-resize-end)
                                                (reset! !mouse-down false))]
                          (js/addEventListener "mouseup" handle-mouse-up)
                          #(js/removeEventListener "mouseup" handle-mouse-up))))
    [:<>
     [:div.absolute.z-2.cursor-nwse-resize
      {:on-mouse-down #(handle-mouse-down :top-left)
       :class "w-[14px] h-[14px] -left-[7px] -top-[7px]"}]
     [:div.absolute.z-1.left-0.w-full.cursor-ns-resize
      {:on-mouse-down #(handle-mouse-down :top)
       :class "h-[4px] -top-[4px]"}]
     [:div.absolute.z-2.cursor-nesw-resize
      {:on-mouse-down #(handle-mouse-down :top-right)
       :class "w-[14px] h-[14px] -right-[7px] -top-[7px]"}]
     [:div.absolute.z-1.top-0.h-full.cursor-ew-resize
      {:on-mouse-down #(handle-mouse-down :right)
       :class "w-[4px] -right-[2px]"}]
     [:div.absolute.z-2.cursor-nwse-resize
      {:on-mouse-down #(handle-mouse-down :bottom-right)
       :class "w-[14px] h-[14px] -right-[7px] -bottom-[7px]"}]
     [:div.absolute.z-1.bottom-0.w-full.cursor-ns-resize
      {:on-mouse-down #(handle-mouse-down :bottom)
       :class "h-[4px] -left-[2px]"}]
     [:div.absolute.z-2.cursor-nesw-resize
      {:on-mouse-down #(handle-mouse-down :bottom-left)
       :class "w-[14px] h-[14px] -left-[7px] -bottom-[7px]"}]
     [:div.absolute.z-1.left-0.top-0.h-full.cursor-ew-resize
      {:on-mouse-down #(handle-mouse-down :left)
       :class "w-[4px]"}]]))

(defn header [{:keys [on-drag on-drag-start on-drag-end] :or {on-drag-start #() on-drag-end #()}}]
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
    [:div.bg-slate-100.hover:bg-slate-200.dark:bg-slate-800.dark:hover:bg-slate-700.cursor-move.w-full.rounded-t-lg
     {:class "h-[14px]"
      :on-mouse-down (fn [event]
                       (on-drag-start)
                       (reset! !mouse-down {:start-x (.-screenX event) :start-y (.-screenY event)}))}]))

(defn resize-top [panel {:keys [top height]} dy]
  (j/assoc-in! panel [:style :height] (str (- height dy) "px"))
  (j/assoc-in! panel [:style :top] (str (+ top dy) "px")))

(defn resize-right [panel {:keys [width]} dx]
  (j/assoc-in! panel [:style :width] (str (+ width dx) "px")))

(defn resize-bottom [panel {:keys [height]} dy]
  (j/assoc-in! panel [:style :height] (str (+ height dy) "px")))

(defn resize-left [panel {:keys [left width]} dx]
  (j/assoc-in! panel [:style :width] (str (- width dx) "px"))
  (j/assoc-in! panel [:style :left] (str (+ left dx) "px")))

(defn show [& content]
  (let [!panel-ref (hooks/use-ref nil)
        !dragging? (hooks/use-state nil)]
    [:div.fixed.bg-white.dark:bg-slate-900.shadow-xl.ring-1.text-slate-800.dark:text-slate-100.hover:ring-2.rounded-lg.flex.flex-col
     {:class (str "z-[1000] " (if @!dragging? "ring-indigo-600 select-none " "ring-slate-300 dark:ring-slate-700 "))
      :ref !panel-ref
      :style {:top 30 :right 30 :width 400 :height 400}}
     [resizer {:on-resize (fn [dir dx dy]
                            (when-let [panel @!panel-ref]
                              (let [rect (j/lookup (.getBoundingClientRect panel))]
                                (case dir
                                  :top-left (do (resize-top panel rect dy)
                                                (resize-left panel rect dx))
                                  :top (resize-top panel rect dy)
                                  :top-right (do (resize-top panel rect dy)
                                                 (resize-right panel rect dx))
                                  :right (resize-right panel rect dx)
                                  :bottom-right (do (resize-bottom panel rect dy)
                                                    (resize-right panel rect dx))
                                  :bottom (resize-bottom panel rect dy)
                                  :bottom-left (do (resize-bottom panel rect dy)
                                                   (resize-left panel rect dx))
                                  :left (resize-left panel rect dx)))))
               :on-resize-start #(reset! !dragging? true)
               :on-resize-end #(reset! !dragging? false)}]
     [header {:on-drag (fn [dx dy]
                         (when-let [panel @!panel-ref]
                           (let [{:keys [left top]} (j/lookup (.getBoundingClientRect panel))]
                             (j/assoc-in! panel [:style :left] (str (+ left dx) "px"))
                             (j/assoc-in! panel [:style :top] (str (+ top dy) "px")))))
              :on-drag-start #(reset! !dragging? true)
              :on-drag-end #(reset! !dragging? false)}]
     (into [:div.p-3.flex-auto.overflow-auto] content)]))
