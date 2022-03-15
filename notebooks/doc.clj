;; # 📓 Doc Browser
^{:nextjournal.clerk/visibility #{:hide-ns :hide}}
(ns ^:nextjournal.clerk/no-cache doc
  (:require [clojure.string :as str]
            [nextjournal.clerk :as clerk]))

^{::clerk/viewer clerk/hide-result}
(def text-input
  {:pred ::clerk/var-from-def
   :fetch-fn (fn [_ x] x)
   :transform-fn (fn [{::clerk/keys [var-from-def]}]
                   {:var-name (symbol var-from-def) :value @@var-from-def})
   :render-fn '(fn [{:keys [var-name value]}]
                 (v/html [:div
                          [:input {:type :text
                                   :autocorrect "off"
                                   :spellcheck "false"
                                   :placeholder "⌨"
                                   :value value
                                   :class "px-3 py-3 placeholder-blueGray-300 text-blueGray-600 relative bg-white bg-white rounded text-sm border border-blueGray-300 outline-none focus:outline-none focus:ring w-full"
                                   :on-input #(v/clerk-eval `(reset! ~var-name ~(.. % -target -value)))}]
                          [:a.absolute.text-4xl {:on-click #(v/clerk-eval `(reset! ~var-name ~(str/join "." (drop-last (str/split value #"\.")))))} "↫"]]))})

^{::clerk/viewer text-input}
(defonce !ns-query (atom "nextjournal.clerk"))
#_(reset! !ns-query "nextjournal.clerk")

^{::clerk/viewers [{:pred seq? :render-fn '#(v/html (into [:div.flex-col.flex] (v/inspect-children %2) %1)) :fetch-opts {:n 20}}
                   {:pred string? :render-fn '(fn [ns] (v/html [:a.text-xs.font-mono.cursor-pointer {:on-click #(v/clerk-eval `(reset! !ns-query ~ns))} ns]))}]}
(def ns-matches
  (filter #(str/starts-with? % @!ns-query) (sort (map str (all-ns)))))

^{::clerk/viewer clerk/hide-result}
(defn var->doc-viewer
  "Takes a clojure `var` and returns a Clerk viewer to display its documentation."
  [var]
  (let [{:keys [doc name arglists]} (meta var)]
    (clerk/html
     [:div.border-t-2.t-6.pt-3
      [:h2.text-lg name]
      (clerk/code (str/join "\n" (mapv (comp pr-str #(concat [name] %)) arglists)))
      (when doc
        (clerk/md doc))])))

#_(var->doc-viewer #'var->doc-viewer)

^{::clerk/viewer clerk/hide-result}
(def var-doc-viewer {:pred ::clerk/var-from-def
                     :transform-fn (comp var->doc-viewer ::clerk/var-from-def)})

^{::clerk/viewer clerk/hide-result}
(defn namespace->doc-viewer [ns]
  (clerk/html
   [:div.flex-auto.overflow-y-auto.p-6.text-sm
    [:div.max-w-6xl.mx-auto
     [:h1.text-2xl (ns-name ns)]
     (when-let [doc (-> ns meta :doc)]
       [:div.mt-4.leading-normal.viewer-markdown.prose
        (clerk/md doc)])
     (into [:<>]
           (map (comp :nextjournal/value var->doc-viewer val))
           (into (sorted-map) (-> ns ns-publics)))]]))

^{::clerk/viewer clerk/hide-result}
(def ns-doc-viewer {:pred #(instance? clojure.lang.Namespace %)
                    :transform-fn namespace->doc-viewer})

(when-let [ns-name (first ns-matches)]
  (clerk/with-viewer ns-doc-viewer (find-ns (symbol ns-name))))
