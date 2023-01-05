;; # 📓 Doc Browser
(ns doc
  {:nextjournal.clerk/visibility {:code :hide :result :hide}}
  (:require [clojure.string :as str]
            [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as viewer]))

(def render-input
  '(fn [!query]
     (prn :query !query)
     [:div.my-1.relative
      [:input {:type :text
               :auto-correct "off"
               :spell-check "false"
               :placeholder "Filter namespaces…"
               :value @!query
               :class "px-3 py-2 relative bg-white bg-white rounded text-base font-sans border border-slate-200 shadow-inner outline-none focus:outline-none focus:ring w-full"
               :on-input #(reset! !query (.. % -target -value))}]
      [:button.absolute.right-2.text-xl.cursor-pointer
       {:class "top-1/2 -translate-y-1/2"
        :on-click #(reset! !query (clojure.string/join "." (drop-last (clojure.string/split @!query #"\."))))} "⏮"]]))

^{::clerk/sync true}
(defonce !ns-query (atom "nextjournal.clerk"))
#_(reset! !ns-query "nextjournal.clerk")


!ns-query

^{::clerk/visibility {:result :show}
  ::clerk/viewer {:render-fn render-input
                  :transform-fn clerk/mark-presented}}
(viewer/->viewer-eval `!ns-query)

^{::clerk/viewers (clerk/add-viewers
                   [{:pred seq?
                     :render-fn '#(into [:div.border.rounded-md.bg-white.shadow.flex.flex-col.mb-1]
                                        (nextjournal.clerk.render/inspect-children %2) %1) :page-size 20}
                    {:pred string?
                     :render-fn '(fn [ns] [:button.text-xs.font-medium.font-sans.cursor-pointer.px-3.py-2.hover:bg-blue-100.text-slate-700.text-left
                                           {:on-click #(reset! doc/!ns-query ns)} ns])}])}

^{::clerk/visibility {:result :show}}
(def ns-matches
  (filter (partial re-find (re-pattern @!ns-query)) (sort (map str (all-ns)))))

(defn var->doc-viewer
  "Takes a clojure `var` and returns a Clerk viewer to display its documentation."
  [var]
  (let [{:keys [doc name arglists]} (meta var)]
    (clerk/html
     [:div.border-t.border-slate-200.pt-6.mt-6
      [:h2 {:style {:margin 0}} name]
      (when (seq arglists)
        [:div.pt-4
         (clerk/code (str/join "\n" (mapv (comp pr-str #(concat [name] %)) arglists)))])
      (when doc
        [:div.mt-4.viewer-markdown.prose
         (clerk/md doc)])])))

#_(var->doc-viewer #'var->doc-viewer)

(defn namespace->doc-viewer [ns]
  (clerk/html
   [:div.text-sm.mt-6
    [:h1 {:style {:margin 0}} (ns-name ns)]
    (when-let [doc (-> ns meta :doc)]
      [:div.mt-4.leading-normal.viewer-markdown.prose
       (clerk/md doc)])
    (into [:<>]
          (map (comp :nextjournal/value var->doc-viewer val))
          (into (sorted-map) (-> ns ns-publics)))]))

(def ns-doc-viewer {:pred #(instance? clojure.lang.Namespace %)
                    :transform-fn (clerk/update-val namespace->doc-viewer)})

^{::clerk/visibility {:result :show}}
(when-let [ns-name (first ns-matches)]
  (clerk/with-viewer ns-doc-viewer (find-ns (symbol ns-name))))



#_(deref nextjournal.clerk.webserver/!doc)
