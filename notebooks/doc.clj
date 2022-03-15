^{:nextjournal.clerk/visibility #{:hide-ns}}
(ns ^:nextjournal.clerk/no-cache doc
  "My _example_ ns docstring."
  (:require [clojure.string :as str]
            [nextjournal.clerk :as clerk]))

#_(filter #(str/starts-with? % "nextjournal.clerk") (sort (map str (all-ns))))

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

(var->doc-viewer #'var->doc-viewer)

(def var-doc-viewer {:pred ::clerk/var-from-def
                     :transform-fn (comp var->doc-viewer ::clerk/var-from-def)})

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

(def ns-doc-viewer {:pred #(instance? clojure.lang.Namespace %)
                    :transform-fn namespace->doc-viewer})

(clerk/with-viewer ns-doc-viewer (find-ns 'nextjournal.clerk))

#_(clerk/serve! {})
