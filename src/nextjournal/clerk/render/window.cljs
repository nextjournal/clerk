(ns nextjournal.clerk.render.window
  (:require ["@codemirror/view" :as cm-view :refer [keymap]]
            [applied-science.js-interop :as j]
            [clojure.string :as str]
            [nextjournal.clerk.render.code :as code]
            [nextjournal.clerk.render.hooks :as hooks]
            [nextjournal.clerk.sci-env.completions :as completions]
            [nextjournal.clojure-mode.keymap :as clojure-mode.keymap]
            [nextjournal.clojure-mode.extensions.eval-region :as eval-region]
            [sci.core :as sci]
            [sci.ctx-store]))

(defn inspect-fn []
  @(resolve 'nextjournal.clerk.render/inspect))

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

(defn header [{:keys [id title on-drag on-drag-start on-drag-end on-close] :or {on-drag-start #() on-drag-end #()}}]
  (let [!mouse-down (hooks/use-state false)
        name (or title id)]
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
    [:div.bg-slate-100.hover:bg-slate-200.dark:bg-slate-800.dark:hover:bg-slate-700.cursor-move.w-full.rounded-t-lg.flex-shrink-0.leading-none.flex.items-center.justify-between
     {:class (if name "h-[24px] " "h-[14px] ")
      :on-mouse-down (fn [event]
                       (on-drag-start)
                       (reset! !mouse-down {:start-x (.-screenX event) :start-y (.-screenY event)}))}
     (when name
       [:span.font-sans.font-medium.text-slate-700
        {:class "text-[12px] ml-[8px] "}
        (or title id)])
     (when on-close
       [:button.text-slate-600.hover:text-slate-900.hover:bg-slate-300.rounded-tr-lg.flex.items-center.justify-center
        {:on-click on-close
         :class "w-[24px] h-[24px]"}
        [:svg {:xmlns "http://www.w3.org/2000/svg" :fill "none" :viewBox "0 0 24 24" :stroke-width "1.5" :stroke "currentColor" :class "w-3 h-3"}
         [:path {:stroke-linecap "round" :stroke-linejoin "round" :d "M6 18L18 6M6 6l12 12"}]]])]))

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

(defn eval-string
  ([source] (sci/eval-string* (sci.ctx-store/get-ctx) source))
  ([ctx source]
   (when-some [code (not-empty (str/trim source))]
     (try {:result (sci/eval-string* ctx code)}
          (catch js/Error e
            {:error (str (.-message e))})))))

(j/defn eval-at-cursor [on-result ^:js {:keys [state]}]
  (some->> (eval-region/cursor-node-string state)
           (eval-string)
           (on-result))
  true)

(j/defn eval-top-level [on-result ^:js {:keys [state]}]
  (some->> (eval-region/top-level-string state)
           (eval-string)
           (on-result))
  true)

(j/defn eval-cell [on-result ^:js {:keys [state]}]
  (-> (.-doc state)
      (str)
      (eval-string)
      (on-result))
  true)

(defn sci-extension [{:keys [modifier on-result]}]
  (.of cm-view/keymap
       (j/lit
        [{:key "Mod-Enter"
          :run (partial eval-cell on-result)}
         {:key (str modifier "-Enter")
          :shift (partial eval-top-level on-result)
          :run (partial eval-at-cursor on-result)}])))

(defn sci-repl []
  (let [!code-str (hooks/use-state "")
        !results (hooks/use-state ())]
    [:div.flex.flex-col.bg-gray-50
     [:div.w-full.border-t.border-b.border-slate-300.shadow-inner.px-2.py-1.bg-slate-100
      [code/editor !code-str {:extensions #js [(.of keymap clojure-mode.keymap/paredit)
                                               completions/completion-source
                                               (sci-extension {:modifier "Alt"
                                                               :on-result #(swap! !results conj {:result %
                                                                                                 :evaled-at (js/Date.)
                                                                                                 :react-key (gensym)})})]}]]
     (into
      [:div.w-full.flex-auto.overflow-auto]
      (map (fn [{:as r :keys [result evaled-at react-key]}]
             ^{:key react-key}
             [:div.border-b.px-2.py-2.text-xs.font-mono
              [:div.font-mono.text-slate-40.flex-shrink-0.text-right
               {:class "text-[9px]"}
               (str (first (.. evaled-at toTimeString (split " "))) ":" (.getMilliseconds evaled-at))]
              [(inspect-fn) result]]))
      @!results)]))

(defn show
  ([content] (show content {}))
  ([content {:as opts :keys [css-class]}]
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
       [header (merge {:on-drag (fn [{:keys [x y dx dy]}]
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
                                      (reset! !docking-ref nil))}
                      opts)]
       [:div {:class (str "flex-auto " (or css-class "p-3 overflow-auto"))} content]]])))
