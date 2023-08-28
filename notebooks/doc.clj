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

(defn escape-pattern-str [s]
  (let [esc-chars "[](){}<>*&^%$#!\\|? "]
    (->> s
         (replace (zipmap esc-chars (map #(str "\\" %) esc-chars)))
         (reduce str)
         str)))

(def ns-matches
  (filter (partial re-find (re-pattern (escape-pattern-str @!ns-query))) (sort (map str (all-ns)))))

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

(defn render-ns [{:keys [name nss vars]}]
  [:div.mt-1
   [:div name]
   (when vars
     (into [:div.ml-3] (map (fn [var]
                              [:div.mt-1.text-xs.italic var])) vars))
   (when nss
     (into [:div.ml-3] (map render-ns) nss))])

(defn ns-node-with-branches [nss-map ns-name]
  (let [sub-nss (get nss-map ns-name)
        vars (some-> ns-name symbol find-ns ns-publics not-empty vals vec)]
    (cond-> {:name ns-name}
      sub-nss (assoc :nss (mapv (partial ns-node-with-branches nss-map) sub-nss))
      vars (assoc :vars vars))))

(defn ns-tree
  ([ns-matches]
   (ns-tree (update-keys (group-by #(butlast (clojure.string/split % #"\.")) ns-matches)
                         (partial clojure.string/join "."))
            ns-matches
            []))
  ([nss-map ns-matches acc]
   (if-some [ns-name (first ns-matches)]
     (recur nss-map
            (remove (some-fn #{ns-name} #(str/starts-with? % (str ns-name ".")))
                    ns-matches)
            (conj acc (ns-node-with-branches nss-map ns-name)))
     acc)))

#_(ns-tree ns-matches)
#_(ns-tree ())

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
              (ns-tree ns-matches)))]
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
