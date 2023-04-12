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
                        (let [handle-mouse-move #(on-drag {:x (.-clientX %) :y (.-clientY %) :dx (.-movementX %) :dy (.-movementY %)})]
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

(defn dock-at-top [panel]
  (-> panel
      (j/assoc-in! [:style :width] "calc(100vw - 10px)")
      (j/assoc-in! [:style :height] "33vh")
      (j/assoc-in! [:style :top] "5px")
      (j/assoc-in! [:style :left] "5px")))

(defn dock-at-right [panel]
  (-> panel
      (j/assoc-in! [:style :width] "33vw")
      (j/assoc-in! [:style :height] "calc(100vh - 10px)")
      (j/assoc-in! [:style :top] "5px")
      (j/assoc-in! [:style :left] "calc(100vw - 33vw - 5px)")))

(defn dock-at-bottom [panel]
  (-> panel
      (j/assoc-in! [:style :width] "calc(100vw - 10px)")
      (j/assoc-in! [:style :height] "33vh")
      (j/assoc-in! [:style :top] "calc(100vh - 33vh - 10px)")
      (j/assoc-in! [:style :left] "5px")))

(defn dock-at-left [panel]
  (-> panel
      (j/assoc-in! [:style :width] "33vw")
      (j/assoc-in! [:style :height] "calc(100vh - 10px)")
      (j/assoc-in! [:style :top] "5px")
      (j/assoc-in! [:style :left] "5px")))

(defn show [& content]
  (let [!panel-ref (hooks/use-ref nil)
        !dragging? (hooks/use-state nil)
        !dockable-at (hooks/use-state nil)
        !docking-ref (hooks/use-ref nil)]
    [:<>
     [:div.fixed.border-2.border-dashed.border-indigo-600.border-opacity-70.bg-indigo-600.bg-opacity-30.pointer-events-none.transition-all.rounded-lg
      {:class (str "z-[999] " (if-let [side @!dockable-at]
                                (str "opacity-100 " (case side
                                                      :top "left-[5px] top-[5px] right-[5px] h-[33vh]"
                                                      :left "left-[5px] top-[5px] bottom-[5px] w-[33vw]"
                                                      :bottom "left-[5px] bottom-[5px] right-[5px] h-[33vh]"
                                                      :right "right-[5px] top-[5px] bottom-[5px] w-[33vw]"))
                                "opacity-0 "))}]
     [:div.fixed.bg-white.dark:bg-slate-900.shadow-xl.text-slate-800.dark:text-slate-100.rounded-lg.flex.flex-col.hover:ring-2
      {:class (str "z-[1000] " (if @!dragging? "ring-indigo-600 select-none ring-2 " "ring-slate-300 dark:ring-slate-700 ring-1 "))
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
      [header {:on-drag (fn [{:keys [x y dx dy]}]
                          (when-let [panel @!panel-ref]
                            (let [{:keys [left top width]} (j/lookup (.getBoundingClientRect panel))
                                  x-edge-offset 20
                                  y-edge-offset 10
                                  vw js/innerWidth
                                  vh js/innerHeight]
                              (reset! !dockable-at (cond
                                                     (zero? x) :left
                                                     (>= x (- vw 2)) :right
                                                     (<= y 0) :top
                                                     (>= y (- vh 2)) :bottom
                                                     :else nil))
                              (reset! !docking-ref @!dockable-at)
                              (j/assoc-in! panel [:style :left] (str (min (- vw x-edge-offset) (max (+ x-edge-offset (- width)) (+ left dx))) "px"))
                              (j/assoc-in! panel [:style :top] (str (min (- vh y-edge-offset) (max y-edge-offset (+ top dy))) "px")))))
               :on-drag-start #(reset! !dragging? true)
               :on-drag-end (fn []
                              (when-let [side @!docking-ref]
                                (let [panel @!panel-ref]
                                  (case side
                                    :top (dock-at-top panel)
                                    :right (dock-at-right panel)
                                    :bottom (dock-at-bottom panel)
                                    :left (dock-at-left panel))))
                              (reset! !dockable-at nil)
                              (reset! !docking-ref nil))}]
      (into [:div.p-3.flex-auto.overflow-auto] content)]]))
