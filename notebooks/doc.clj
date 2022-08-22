;; # 📓 Doc Browser
(ns doc
  {:nextjournal.clerk/visibility {:code :hide}}
  (:require [clojure.string :as str]
            [nextjournal.clerk :as clerk]))

^{::clerk/visibility {:result :hide}}
(def text-input
  {:pred ::clerk/var-from-def
   :fetch-fn (fn [_ x] x)
   :transform-fn (comp (clerk/update-val (fn [{::clerk/keys [var-from-def]}]
                                           {:var-name (symbol var-from-def) :value @@var-from-def}))
                       clerk/mark-presented)
   :render-fn '(fn [{:keys [var-name value]}]
                 (v/html [:div.my-1.relative
                          [:input {:type :text
                                   :auto-correct "off"
                                   :spell-check "false"
                                   :placeholder "Filter namespaces…"
                                   :value value
                                   :class "px-3 py-2 relative bg-white bg-white rounded text-base font-sans border border-slate-200 shadow-inner outline-none focus:outline-none focus:ring w-full"
                                   :on-input #(v/clerk-eval `(reset! ~var-name ~(.. % -target -value)))}]
                          [:button.absolute.right-2.text-xl.cursor-pointer
                           {:class "top-1/2 -translate-y-1/2"
                            :on-click #(v/clerk-eval `(reset! ~var-name ~(clojure.string/join "." (drop-last (clojure.string/split value #"\.")))))} "⏮"]]))})

^{::clerk/viewer text-input}
(defonce !ns-query (atom "nextjournal.clerk"))
#_(reset! !ns-query "nextjournal.clerk")

^{::clerk/viewers (clerk/add-viewers
                   [{:pred seq?
                     :render-fn '#(v/html (into [:div.border.rounded-md.bg-white.shadow.flex.flex-col.mb-1]
                                                (v/inspect-children %2) %1)) :fetch-opts {:n 20}}
                    {:pred string?
                     :render-fn '(fn [ns] (v/html [:button.text-xs.font-medium.font-sans.cursor-pointer.px-3.py-2.hover:bg-blue-100.text-slate-700.text-left
                                                   {:on-click #(v/clerk-eval `(reset! !ns-query ~ns))} ns]))}])}
(def ns-matches
  (filter (partial re-find (re-pattern @!ns-query)) (sort (map str (all-ns)))))

^{::clerk/visibility {:result :hide}}
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

^{::clerk/visibility {:result :hide}}
(def var-doc-viewer {:pred ::clerk/var-from-def
                     :transform-fn (clerk/update-val (comp var->doc-viewer ::clerk/var-from-def))})

^{::clerk/visibility {:result :hide}}
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

^{::clerk/visibility {:result :hide}}
(def ns-doc-viewer {:pred #(instance? clojure.lang.Namespace %)
                    :transform-fn (clerk/update-val namespace->doc-viewer)})

(when-let [ns-name (first ns-matches)]
  (clerk/with-viewer ns-doc-viewer (find-ns (symbol ns-name))))



#_(deref nextjournal.clerk.webserver/!doc)
