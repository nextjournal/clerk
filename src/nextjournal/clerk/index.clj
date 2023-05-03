(ns nextjournal.clerk.index
  {:nextjournal.clerk/visibility {:code :hide :result :hide}}
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as v]
            [nextjournal.clerk.builder :as builder]))

(def !paths (delay (builder/index-paths)))

(def index-viewer
  {:render-fn '(fn [directories opts]
                 [:div.not-prose.font-sans
                  (into [:div]
                        (map
                         (fn [[dir files]]
                           [:div.mb-6
                            (when (seq dir)
                              [:div.flex.items-center.gap-2.mb-1
                               [:svg {:xmlns "http://www.w3.org/2000/svg" :fill "none" :viewBox "0 0 24 24" :stroke-width "1.5" :stroke "currentColor" :class "w-4 h-4"}
                                [:path {:stroke-linecap "round" :stroke-linejoin "round" :d "M3.75 9.776c.112-.017.227-.026.344-.026h15.812c.117 0 .232.009.344.026m-16.5 0a2.25 2.25 0 00-1.883 2.542l.857 6a2.25 2.25 0 002.227 1.932H19.05a2.25 2.25 0 002.227-1.932l.857-6a2.25 2.25 0 00-1.883-2.542m-16.5 0V6A2.25 2.25 0 016 3.75h3.879a1.5 1.5 0 011.06.44l2.122 2.12a1.5 1.5 0 001.06.44H18A2.25 2.25 0 0120.25 9v.776"}]]
                               [:span.font-bold dir]])
                            (into [:ul
                                   {:class (when (seq dir) "ml-[27px]")}]
                                  (map (fn [file] [:li.mb-1
                                                  [:a.text-blue-600.hover:underline {:href (nextjournal.clerk.viewer/doc-url file)} file]]))
                                  files)]))
                        directories)])
   :transform-fn (comp
                  clerk/mark-presented
                  (clerk/update-val #(group-by (fn [path]
                                                 (str/join fs/file-separator (butlast (fs/path path)))) %)))})

(def filter-input-viewer
  (assoc v/viewer-eval-viewer
         :var-from-def true
         :render-fn '(fn [!!state]
                       (let [!state (if (var? !!state) (resolve !!state) !!state)
                             !input-el (nextjournal.clerk.render.hooks/use-ref nil)]
                         (nextjournal.clerk.render.hooks/use-effect
                          (fn []
                            (let [keydown-handler (fn [e]
                                                    (when @!input-el
                                                      (when (and (.-metaKey e) (= (.-key e) "j"))
                                                        (.focus @!input-el))
                                                      (when (and (= @!input-el js/document.activeElement)
                                                                 (= (.-key e) "Escape"))
                                                        (.blur @!input-el))))]
                              (js/document.addEventListener "keydown" keydown-handler)
                              #(js/document.removeEventListener "keydown" keydown-handler)))
                          [!input-el])
                         [:div.relative
                          [:input.pl-8.py-2.bg-white.rounded-lg.font-medium.font-sans.border.w-full.shadow-inner.transition-all
                           {:class "pr-[60px]"
                            :type :text
                            :placeholder "Type to filter…"
                            :value @!state
                            :on-input #(reset! !state (.. % -target -value))
                            :ref !input-el}]
                          [:div.text-slate-400.absolute
                           {:class "left-[10px] top-[11px]"}
                           [:svg
                            {:xmlns "http://www.w3.org/2000/svg" :fill "none" :viewBox "0 0 24 24"
                             :stroke-width "1.5" :stroke "currentColor" :class "w-[20px] h-[20px]"}
                            [:path {:stroke-linecap "round" :stroke-linejoin "round" :d "M21 21l-5.197-5.197m0 0A7.5 7.5 0 105.196 5.196a7.5 7.5 0 0010.607 10.607z"}]]]
                          [:div.absolute.font-inter.text-slate-400.text-sm.font-medium.border.rounded-md.tracking-wide.px-1.shadow-sm.pointer-events-none
                           {:class "right-[10px] top-[9px]"}
                           "⌘J"]]))))

(defn query-fn [q path]
  (str/includes? (str/lower-case path) (str/lower-case q)))

{::clerk/visibility {:result :show}}

(clerk/html [:h2 {:style {:margin-bottom "-0.5rem"}} (str (last (fs/cwd)))])

^{::clerk/sync true ::clerk/viewer filter-input-viewer}
(defonce !filter (atom ""))

(let [{:keys [paths error]} @!paths]
  (cond
    error (clerk/md error)
    paths (clerk/with-viewer index-viewer (filter (partial query-fn @!filter) paths))))
