(ns nextjournal.clerk.render.log
  (:require ["@codemirror/view" :as cm-view :refer [keymap]]
            [applied-science.js-interop :as j]
            [clojure.string :as str]
            [nextjournal.clerk.render.code :as code]
            [nextjournal.clerk.render.hooks :as hooks]
            [nextjournal.clerk.render.localstorage :as localstorage]
            [nextjournal.clerk.render.panel :as panel]
            [nextjournal.clerk.viewer :as viewer]
            [nextjournal.clerk.sci-env.completions :as completions]
            [nextjournal.clojure-mode.keymap :as clojure-mode.keymap]
            [nextjournal.clojure-mode.extensions.eval-region :as eval-region]
            [reagent.core :as reagent]
            [sci.core :as sci]
            [sci.ctx-store]))

(defonce !state
  (reagent/atom (merge {:visible? false :panel-height 420 :selected-event-name :all}
                       (localstorage/get-item "log"))))

(add-watch !state ::persist-log-state (fn [_ _ _ state]
                                        (when-not (:resizing? state)
                                          (localstorage/set-item! "log" state))))

(defonce !history (reagent/atom []))

(defn show []
  (swap! !state assoc :visible? true))

(defn visible? []
  (:visible? @!state))

(defn log [event-name & pairs]
  (swap! !history conj {:event-name event-name
                        :timestamp (js/Date.)
                        :pairs pairs}))

(defn inspect-fn []
  @(resolve 'nextjournal.clerk.render/inspect))

(defn log-line [{:keys [event-name timestamp pairs]}]
  [:div.flex.gap-3.p-1.border-b {:class "min-h-[32px]"}
   (into [:div.flex-auto.text-xs.font-mono]
         (map (fn [[k v]]
                [:div.flex.gap-3
                 [:div.whitespace-nowrap [(inspect-fn) k]]
                 [:div [(inspect-fn) v]]]))
         (partition 2 pairs))
   [:div
    [:div.text-white.rounded-sm.font-sans.flex-shrink-0.whitespace-nowrap.leading-none
     {:class "text-[10px] px-[5px] py-[3px] bg-indigo-600 mt-[3px]"}
     event-name]]
   [:div
    [:div.font-mono.flex-shrink-0.whitespace-nowrap.pr-1
     {:class "text-[10px] mt-[4px]"}
     (.toLocaleTimeString timestamp "en-US")]]])

(def cm-theme
  (.theme cm-view/EditorView
          (j/lit {".cm-content" {:white-space "pre-wrap"
                                 :padding "0"
                                 :min-height "60px"
                                 :flex "1 1 0"}
                  "&.cm-focused" {:outline "0 !important"}
                  ".cm-line" {:padding "0 4px"
                              :line-height "1.4"
                              :font-size "13px"
                              :font-family "'Fira Code', monospace"}
                  ".cm-matchingBracket" {:border-bottom "1px solid var(--teal-color)"
                                         :color "inherit"}
                  ".cm-gutters" {:background "transparent"
                                 :border "none"}
                  ".cm-gutterElement" {:margin-left "5px"}
                  ;; only show cursor when focused
                  ".cm-cursor" {:visibility "hidden"}
                  "&.cm-focused .cm-cursor" {:visibility "visible"
                                             :animation "steps(1) cm-blink 1.2s infinite"}
                  ".cm-tooltip.cm-tooltip-autocomplete" {:border "0"
                                                         :border-radius "6px"
                                                         :box-shadow "0 4px 6px -1px rgb(0 0 0 / 0.1), 0 2px 4px -2px rgb(0 0 0 / 0.1)"
                                                         "& > ul" {:font-size "12px"
                                                                   :font-family "'Fira Code', monospace"
                                                                   :background "rgb(241 245 249)"
                                                                   :border "1px solid rgb(203 213 225)"
                                                                   :border-radius "6px"}}
                  ".cm-tooltip-autocomplete ul li" {:color "#333"}
                  ".cm-tooltip-autocomplete ul li[aria-selected]" {:background "rgb(79 70 229) !important"
                                                                   :color "#fff !important"}
                  ".cm-tooltip.cm-tooltip-hover" {:background "rgb(241 245 249)"
                                                  :border-radius "6px"
                                                  :border "1px solid rgb(203 213 225)"
                                                  :box-shadow "0 4px 6px -1px rgb(0 0 0 / 0.1), 0 2px 4px -2px rgb(0 0 0 / 0.1)"}})))

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
        [{:key "Alt-Enter"
          :run (partial eval-cell on-result)}
         {:key (str "Mod-Enter")
          :shift (partial eval-top-level on-result)
          :run (partial eval-at-cursor on-result)}])))

(defn sci-repl []
  (let [!code-str (hooks/use-state "")
        !results (hooks/use-state ())]
    [:div.w-full.h-full.appearance-none.font-mono.text-xs.rounded-md.border.border-slate-300.px-2.py-1.bg-white
     [code/editor !code-str {:extensions #js [cm-theme
                                              (cm-view/placeholder "Evaluate forms with Command-Enter")
                                              (.of keymap clojure-mode.keymap/paredit)
                                              completions/doc-tooltip
                                              completions/completion-source
                                              (sci-extension {:on-result #(log :eval :sci %)})]}]]))

(defn event-name-button [{:keys [event-name selected? on-select]}]
  [:div.rounded-sm.font-sans.flex-shrink-0.whitespace-nowrap.leading-none.font-inter
   {:on-click #(swap! !state assoc :selected-event-name event-name)
    :class (str "text-[11px] px-[5px] py-[3px] cursor-pointer "
                (if selected? "bg-indigo-600 text-white" "text-indigo-600 hover:bg-indigo-300"))}
   event-name])

(defn spacer []
  [:div {:style {:height (:panel-height @!state)}}])

(defn resizer []
  [:div.absolute.left-0.right-0.group
   {:class "h-[10px] -top-[5px]"}
   [:div.opacity-0.group-hover:opacity-100.transition-all.absolute.left-0.border-b.border-indigo-600.w-full.pointer-events-none
    {:class "top-[4px] border-[2px]"}]
   [panel/resizer {:axis :y
                   :on-resize-start #(swap! !state assoc :resizing? true)
                   :on-resize (fn [dir _ dy]
                                (swap! !state update :panel-height #(if (= dir :top) (+ % dy) (- % dy))))
                   :on-resize-end #(swap! !state dissoc :resizing?)}]])

(defn panel []
  (let [{:keys [panel-height resizing? selected-event-name]} @!state]
    [:div.fixed.left-0.right-0.bottom-0.border-t.border-slate-300.flex.flex-col.bg-white
     {:class (when resizing? "select-none")
      :style {:height (:panel-height @!state) :zIndex 2000 :box-shadow "0 -3px 3px rgba(0,0,0,.03)"}}
     [:div.bg-slate-100.p-1.border-b.border-slate-300.flex.items-center.gap-3
      [:div.text-slate-700.hover:text-slate-900.cursor-pointer
       {:on-click #(swap! !state assoc :visible? false)}
       [:svg {:xmlns "http://www.w3.org/2000/svg" :fill "none" :viewBox "0 0 24 24" :stroke-width "1.5" :stroke "currentColor" :class "w-[16px] h-[16px]"}
        [:path {:stroke-linecap "round" :stroke-linejoin "round" :d "M6 18L18 6M6 6l12 12"}]]]
      (into [:div.flex.items-center.gap-1
             [event-name-button {:event-name :all
                                 :selected? (= :all selected-event-name)}]]
            (map (fn [event-name] [event-name-button {:event-name event-name
                                                     :selected? (= event-name selected-event-name)}]))
            (distinct (map :event-name @!history)))]
     [:div.bg-white.flex-auto.overflow-y-auto
      (into [:div]
            (map (fn [log] [log-line log]))
            (if (= selected-event-name :all)
              @!history
              (filter #(= (:event-name %) selected-event-name) @!history)))]
     [:div.bg-slate-100.p-1.border-t.border-slate-300.flex.items-center
      [sci-repl]]
     [resizer]]))
