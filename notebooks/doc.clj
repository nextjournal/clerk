^{:nextjournal.clerk/visibility #{:hide-ns}}
(ns ^:nextjournal.clerk/no-cache doc
  "My _example_ ns docstring."
  (:require [clojure.string :as str]
            [nextjournal.clerk :as clerk]))

(def var-meta-keys
  #{:arglists :col :column :doc :end-col :end-row :file :line :name #_ :ns})


(defn var-meta->doc [{:keys [doc name arglists]}]
  (clerk/html [:div.border-t-2.mt-6.pt-6
               [:h2.text-lg name]
               (clerk/code (str/join "\n" (mapv (comp pr-str #(concat [name] %)) arglists)))
               (when doc
                 (clerk/md doc))]))

(def var-viewer {:pred ::clerk/var-from-def
                 :transform-fn (comp var-meta->doc meta ::clerk/var-from-def)})


(defn namespace->doc [ns]
  (clerk/html [:div.flex-auto.overflow-y-auto.p-6.text-sm
               [:div.max-w-6xl.mx-auto
                [:h1.text-2xl (ns-name ns)]
                (when-let [doc (-> ns meta :doc)]
                  [:div.mt-4.leading-normal.viewer-markdown
                   (clerk/md doc)])
                (into [:<>]
                      (map (comp var-meta->doc meta val))
                      (into (sorted-map) (-> ns ns-publics)))]]))


#_(namespace->doc *ns*)

(-> *ns* ns-publics)


(defn hello "What a _wonderful_ world." []
  :what?)

(defn hello-again "What a _wonderful_ world, again."
  ([] (hello-again "hi"))
  ([s] s)
  ([s a] s a))

(var-meta->doc (meta #'hello-again))

(def ns-viewer {:pred #(instance? clojure.lang.Namespace %)
                :transform-fn namespace->doc})

(clerk/with-viewer ns-viewer *ns*)

(nextjournal.clerk.viewer/describe (clerk/with-viewer ns-viewer *ns*))

#_(clerk/serve! {})
