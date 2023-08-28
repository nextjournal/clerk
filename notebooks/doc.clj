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
               :class "px-2 py-1 relative bg-white bg-white rounded border border-slate-200 shadow-inner outline-none focus:outline-none focus:ring w-full text-sm font-sans"
               :on-input #(reset! !query (.. % -target -value))}]
      [:button.absolute.right-2.text-xl.cursor-pointer
       {:class "top-1/2 -translate-y-1/2"
        :on-click #(reset! !query (clojure.string/join "." (drop-last (clojure.string/split @!query #"\."))))} "⏮"]]))

^{::clerk/sync true}
(defonce !ns-query (atom "nextjournal.clerk"))
#_(reset! !ns-query "nextjournal.clerk")

(def ns-matches
  (filter (partial re-find (re-pattern @!ns-query)) (sort (map str (all-ns)))))

(defn var->doc-viewer
  "Takes a clojure `var` and returns a Clerk viewer to display its documentation."
  [var]
  (let [{:keys [doc name arglists]} (meta var)]
    (clerk/html
     [:div.border-t.border-slate-200.pt-6.mt-6
      [:div.font-sans.font-bold.text-base {:style {:margin 0}} name]
      (when (seq arglists)
        [:div.pt-4
         (clerk/code (str/join "\n" (mapv (comp pr-str #(concat [name] %)) arglists)))])
      (when doc
        [:div.mt-4.viewer-markdown.prose
         (clerk/md doc)])])))

(defn render-var [{:keys [name]}]
  [:div.text-red-500 name])

(defn render-ns [{:keys [name nss vars]}]
  [:div
   [:div name]
   (when vars
     (into [:div.ml-3] (map render-var) vars))
   (when nss
     (into [:div.ml-3] (map render-ns) nss))])

^{::clerk/visibility {:result :show}}
(clerk/html
 (let [ns-str (first ns-matches)]
   [:<>
    [:style ".markdown-viewer { padding: 0 !important; }"]
    [:div.w-screen.h-screen.flex.fixed.left-0.top-0.bg-white.dark:bg-slate-950
     [:div.border-r.py-3.flex-shrink-0.overflow-y-auto {:class "w-[300px]"}
      [:div.px-3
       (clerk/with-viewer {:render-fn render-input
                           :transform-fn clerk/mark-presented} (viewer/->viewer-eval `!ns-query))]
      (when ns-str
        (into [:div.text-sm.font-sans.px-5.mt-3]
              (map render-ns)
              [{:name "nextjournal"
                :nss [{:name "nextjournal.clerk"
                       :vars [{:name "stop"}
                              {:name "watch"}]
                       :nss [{:name "nextjournal.clerk.viewer"
                              :vars [{:name "!viewers"}
                                     {:name "->ViewerEval"}]}]}]}]))
      #_(clerk/with-viewers [{:pred seq?
                              :render-fn '#(into [:div.flex.flex-col]
                                                 (nextjournal.clerk.render/inspect-children %2) %1) #_#_:page-size 20}
                             {:pred string?
                              :render-fn '(fn [ns]
                                            [:button.text-xs.font-sans.cursor-pointer.px-5.py-1.hover:bg-indigo-100.text-left
                                             {:on-click #(reset! doc/!ns-query ns)} ns])}] (as-tree (map #(str/split % #"\.") ns-matches)))]
     [:div.flex-auto.max-h-screen.overflow-y-auto.px-8.py-5
      (if ns-str
        (let [ns (find-ns (symbol ns-str))]
          [:<>
           [:div.font-bold.font-sans.text-xl {:style {:margin 0}} (ns-name ns)]
           (when-let [doc (-> ns meta :doc)]
             [:div.mt-4.leading-normal.viewer-markdown.prose
              (clerk/md doc)])
           (into [:<>]
                 (map (comp :nextjournal/value var->doc-viewer val))
                 (into (sorted-map) (-> ns ns-publics)))])
        [:div "No namespaces found."])]]]))

#_(deref nextjournal.clerk.webserver/!doc)
