(ns nextjournal.clerk.render.table
  (:require ["vh-sticky-table-header" :as sticky-table-header]
            [nextjournal.clerk.render.hooks :as hooks]
            [nextjournal.clerk.render :as render]))

(defn render-table-with-sticky-header [& children]
  (let [!table-ref (hooks/use-ref nil)
        !table-clone-ref (hooks/use-ref nil)]
    (hooks/use-layout-effect (fn []
                               (when (and @!table-ref (.querySelector @!table-ref "thead") @!table-clone-ref)
                                 (let [sticky (sticky-table-header/StickyTableHeader. @!table-ref @!table-clone-ref #js{:max 0})]
                                   (fn [] (.destroy sticky))))))
    [:div
     [:div.overflow-x-auto.overflow-y-hidden
      {:style {:width "max-content"
               :max-width "100%"}}
      (into [:table.text-xs.sans-serif.text-gray-900.dark:text-white.not-prose {:ref !table-ref}] children)]
     [:div.overflow-x-auto.overflow-y-hidden.w-full.shadow.sticky-table-header
      [:table.text-xs.sans-serif.text-gray-900.dark:text-white.not-prose {:ref !table-clone-ref :style {:margin 0}}]]]))

(defn render-table-markup [head+body opts]
  [:div
   (into [render-table-with-sticky-header]
         (render/inspect-children opts)
         head+body)])


(defn render-table-head [header-row {:as opts :keys [path number-col?]}]
  [:thead
   (into [:tr]
         (map-indexed (fn [i {:as header-cell :nextjournal/keys [value]}]
                        (let [title (when (or (string? value) (keyword? value) (symbol? value))
                                      value)]
                          [:th.pl-6.pr-2.py-1.align-bottom.font-medium.top-0.z-10.bg-white.dark:bg-slate-900.border-b.border-gray-300.dark:border-slate-700
                           (cond-> {:class (when (and (ifn? number-col?) (number-col? i)) "text-right")} title (assoc :title title))
                           (render/inspect-presented opts header-cell)]))) header-row)])

(defn render-table-body [rows opts]
  (into [:tbody] (map-indexed (fn [idx row] (render/inspect-presented (update opts :path conj idx) row))) rows))

(defn render-table-row [row {:as opts :keys [path number-col?]}]
  (into [:tr.hover:bg-gray-200.dark:hover:bg-slate-700
         {:class (if (even? (peek path)) "bg-black/5 dark:bg-gray-800" "bg-white dark:bg-gray-900")}]
        (map-indexed (fn [idx cell] [:td.pl-6.pr-2.py-1 (when (and (ifn? number-col?) (number-col? idx)) {:class "text-right"}) (render/inspect-presented opts cell)])) row))


(defn render-table-elision [{:as fetch-opts :keys [total offset unbounded?]} {:keys [num-cols]}]
  [render/consume-view-context
   :fetch-fn
   (fn [fetch-fn]
     [:tr.border-t.dark:border-slate-700
      [:td.py-1.relative
       {:col-span num-cols
        :class (if (fn? fetch-fn)
                 "bg-indigo-50 hover:bg-indigo-100 dark:bg-gray-800 dark:hover:bg-slate-700 cursor-pointer"
                 "text-gray-400 text-slate-500")
        :on-click (fn [_] (when (fn? fetch-fn)
                            (fetch-fn fetch-opts)))}
       (let [label [:<>
                    (- total offset)
                    (when unbounded? "+")
                    (if (fn? fetch-fn)
                      [:span " more" [:span.absolute "..."]]
                      " more elided")]]
         [:<>
          [:span.invisible.pointer-events-none
           label]
          [:span.sticky.inline-block
           {:class "left-1/2 -translate-x-1/2"}
           label]])]])])

(defn render-table-number [x]
  [:span.tabular-nums (if (js/Number.isNaN x) "NaN" (str x))])



(def x-icon
  [:svg.h-4.w-4 {:xmlns "http://www.w3.org/2000/svg" :viewBox "0 0 20 20" :fill "currentColor"}
   [:path {:fill-rule "evenodd" :d "M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" :clip-rule "evenodd"}]])

(def check-icon
  [:svg.h-4.w-4 {:xmlns "http://www.w3.org/2000/svg" :viewBox "0 0 20 20" :fill "currentColor"}
   [:path {:fill-rule "evenodd" :d "M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" :clip-rule "evenodd"}]])

(defn render-table-error [[data]]
  ;; currently boxing the value in a vector to retain the type info
  ;; TODO: find a better way to do this
  [:div.bg-red-100.dark:bg-gray-800.px-6.py-4.rounded-md.text-xs.dark:border-2.dark:border-red-400.not-prose
   [:h4.mt-0.uppercase.text-xs.dark:text-red-400.tracking-wide "Table Error"]
   [:p.mt-4.font-medium "Clerk’s table viewer does not recognize the format of your data:"]
   [:div.mt-2.flex
    [:div.text-red-500.mr-2 x-icon]
    [render/inspect-presented data]]
   [:p.mt-4.font-medium "Currently, the following formats are supported:"]
   [:div.mt-2.flex.items-center
    [:div.text-green-500.mr-2 check-icon]
    [render/inspect {:column-1 [1 2]
                     :column-2 [3 4]}]]
   [:div.mt-2.flex.items-center
    [:div.text-green-500.mr-2 check-icon]
    [render/inspect [{:column-1 1 :column-2 3} {:column-1 2 :column-2 4}]]]
   [:div.mt-2.flex.items-center
    [:div.text-green-500.mr-2 check-icon]
    [render/inspect [[1 3] [2 4]]]]
   [:div.mt-2.flex.items-center
    [:div.text-green-500.mr-2 check-icon]
    [render/inspect {:head [:column-1 :column-2]
                     :rows [[1 3] [2 4]]}]]])
