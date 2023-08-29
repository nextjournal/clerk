(ns doc
  {:nextjournal.clerk/visibility {:code :hide :result :hide}}
  (:require [clojure.string :as str]
            [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as viewer]))

(def render-input
  '(fn [!query]
     (nextjournal.clerk.render.hooks/use-effect
      (fn []
        (let [keydown-handler (fn [event]
                                (when (and (.-metaKey event) (= "k" (.-key event)))
                                  (.focus (js/document.getElementById "search-nss"))))]
          (js/document.addEventListener "keydown" keydown-handler)
          #(js/document.removeEventListener "keydown" keydown-handler))))
     [:div.my-1.relative
      [:input#search-nss.px-2.py-1.relative.bg-white.dark:bg-slate-900.bg-white.rounded.border.dark:border-slate-700.shadow-inner.outline-none.focus:outline-none.focus:ring-2.focus.ring-indigo-500.hover:border-slate-400.focus:hover:border-slate-200.w-full.text-sm.font-sans.dark:hover:border-slate-600.dark:focus:border-sslate-700
       {:type :text
        :auto-correct "off"
        :spell-check "false"
        :placeholder "Search namespaces…"
        :value @!query
        :on-input #(reset! !query (.. % -target -value))}]
      [:div.text-xs.absolute.right-2.text-slate-400.dark:text-slate-400.font-inter.tracking-widest.pointer-events-none
       {:class "top-1/2 -translate-y-1/2"} "⌘K"]
      #_[:button.absolute.right-2.text-xl.cursor-pointer
         {:class "top-1/2 -translate-y-1/2"
          :on-click #(reset! !query (clojure.string/join "." (drop-last (clojure.string/split @!query #"\."))))} "⏮"]]))

^{::clerk/sync true}
(defonce !ns-query (atom ""))
#_(reset! !ns-query "")

^{::clerk/sync true}
(defonce !active-ns (atom "nextjournal.clerk.viewer"))
#_(reset! !active-ns "nextjournal.clerk.viewer")

(defn escape-pattern-str [s]
  (let [esc-chars "[](){}<>*&^%$#!\\|? "]
    (->> s
         (replace (zipmap esc-chars (map #(str "\\" %) esc-chars)))
         (reduce str)
         str)))

(defn match-nss [s]
  (filter (partial re-find (re-pattern (escape-pattern-str s))) (sort (map str (all-ns)))))

(def ns-matches
  (match-nss @!ns-query))

(defn var->doc-viewer
  "Takes a clojure `var` and returns a Clerk viewer to display its documentation."
  [var]
  (let [{:keys [doc arglists] var-name :name} (meta var)]
    (clerk/html
     [:div.border-t.dark:border-slate-800.pt-6.mt-6
      {:id (name (symbol var))}
      [:div.font-sans.font-bold.text-base {:style {:margin 0}} var-name]
      (when (seq arglists)
        [:div.pt-4
         (clerk/code (str/join "\n" (mapv (comp pr-str #(concat [var-name] %)) arglists)))])
      (when doc
        [:div.mt-4.viewer-markdown.prose
         (clerk/md doc)])])))

(defn render-ns [{:keys [name nss vars]}]
  [:div.mt-1
   [:div.hover:underline.cursor-pointer.hover:text-indigo-600.dark:hover:text-white.whitespace-nowrap
    {:class (when (= @!active-ns name) "font-bold")
     :on-click (viewer/->viewer-eval `(fn []
                                        (reset! !active-ns ~name)
                                        (reset! !ns-query "")))} name]
   (when (and vars (= @!active-ns name))
     [:<>
      (into [:div.text-xs.font-sans.mt-1.ml-3.mb-3]
            (map (fn [var]
                   [:div.mt-1.hover:text-indigo-600.dark:hover:text-white.cursor-pointer.hover:underline
                    {:on-click (viewer/->viewer-eval `(fn []
                                                        (when-some [el (js/document.getElementById ~(str var))]
                                                          (.scrollIntoView el))))}
                    var]))
            vars)
      (when nss
        [:div.border-b.dark:border-slate-800.mb-3])])
   (when nss
     (into [:div.ml-3] (map render-ns) nss))])

(defn ns-node-with-branches [nss-map ns-name]
  (let [sub-nss (get nss-map ns-name)
        vars (some-> ns-name symbol find-ns ns-publics not-empty keys vec sort)]
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

(defn parent-ns [ns-str]
  (when (str/includes? ns-str ".")
    (str/join "." (butlast (str/split ns-str #"\.")))))

(defn prepend-parent [nss]
  (when-let [parent (parent-ns (first nss))]
    (cons parent nss)))

(defn path-to-ns [ns-str]
  (last (take-while some? (iterate prepend-parent [ns-str]))))

^{::clerk/visibility {:result :show}}
(clerk/html
 [:<>
  [:style ".markdown-viewer { padding: 0 !important; }"]
  [:div.w-screen.h-screen.flex.fixed.left-0.top-0.bg-white.dark:bg-slate-950
   [:div.border-r.dark:border-slate-800.flex-shrink-0.flex.flex-col {:class "w-[300px]"}
    [:div.px-3.py-3.border-b.dark:border-slate-800
     (clerk/with-viewer {:render-fn render-input
                         :transform-fn clerk/mark-presented} (viewer/->viewer-eval `!ns-query))]
    [:div.pb-5.flex-auto.overflow-y-auto
     (cond (not (str/blank? @!ns-query))
           [:div
            [:div.tracking-wider.uppercase.text-slate-500.dark:text-slate-400.px-5.font-sans.text-xs.mt-5 "Search results"]
            (into [:div.text-sm.font-sans.px-5.mt-3]
                  (map render-ns)
                  (ns-tree ns-matches))]
           (= @!active-ns :all)
           [:div
            [:div.tracking-wider.uppercase.text-slate-500.dark:text-slate-400.px-5.font-sans.text-xs.mt-5 "All namespaces"]
            (into [:div.text-sm.font-sans.px-5.mt-2]
                  (map render-ns)
                  (ns-tree (sort (map (comp str ns-name) (all-ns)))))]
           :else
           (let [path (path-to-ns @!active-ns)]
             [:<>
              [:div
               [:div
                [:div.tracking-wider.uppercase.text-slate-500.dark:text-slate-400.px-5.font-sans.text-xs.mt-5.mb-2 "Nav"]
                (when-some [ns-name (some-> (str/join "." (butlast (str/split @!active-ns #"\."))) symbol find-ns ns-name str)]
                  [:div.px-5.font-sans.text-xs.mt-1.hover:text-indigo-600.dark:hover:text-white.hover:underline.cursor-pointer
                   {:on-click (viewer/->viewer-eval `(fn []
                                                       (reset! !active-ns ~ns-name)
                                                       (reset! !ns-query "")))}
                   "One level up"])
                [:div.px-5.font-sans.text-xs.mt-1.hover:text-indigo-600.dark:hover:text-white.hover:underline.cursor-pointer
                 {:on-click (viewer/->viewer-eval `(fn []
                                                     (reset! !active-ns :all)
                                                     (reset! !ns-query "")))}
                 "All namespaces"]]
               [:div.tracking-wider.uppercase.text-slate-500.dark:text-slate-400.px-5.font-sans.text-xs.mt-5 "Current namespace"]
               (into [:div.text-sm.font-sans.px-5.mt-2]
                     (map render-ns)
                     (ns-tree (match-nss @!active-ns)))]]))]]
   [:div.flex-auto.max-h-screen.overflow-y-auto.px-8.py-5
    (let [ns (some-> @!active-ns symbol find-ns)]
      (cond
        ns [:<>
            [:div.font-bold.font-sans.text-xl {:style {:margin 0}} (ns-name ns)]
            (when-let [doc (-> ns meta :doc)]
              [:div.mt-4.leading-normal.viewer-markdown.prose
               (clerk/md doc)])
            (into [:<>]
                  (map (comp :nextjournal/value var->doc-viewer val))
                  (into (sorted-map) (-> ns ns-publics)))]
        @!active-ns [:<>
                     [:div.font-bold.font-sans.text-xl {:style {:margin 0}} (if (= @!active-ns :all)
                                                                              "All namespaces in classpath"
                                                                              @!active-ns)]
                     (into [:div.mt-2]
                           (map (fn [ns-str]
                                  [:div.pt-5.mt-5.border-t.dark:border-slate-800.hover:text-indigo-600.cursor-pointer.group
                                   {:on-click (viewer/->viewer-eval `(fn []
                                                                       (reset! !active-ns ~ns-str)
                                                                       (reset! !ns-query "")))}
                                   [:div.font-sans.text-base.font-bold.group-hover:underline
                                    {:style {:margin 0}}
                                    ns-str]
                                   (when-let [doc (some-> ns-str symbol find-ns meta :doc)]
                                     [:div.mt-2.leading-normal.viewer-markdown.prose.text-sm
                                      (clerk/md doc)])]))
                           (if (= :all @!active-ns)
                             (sort (map :name (ns-tree (map (comp str ns-name) (all-ns)))))
                             (match-nss @!active-ns)))]
        :else [:div "No namespaces found."]))]]])

#_(deref nextjournal.clerk.webserver/!doc)

