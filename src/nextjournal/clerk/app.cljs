(ns nextjournal.clerk.app
  "Clerk CLJS notebooks"
  (:require [nextjournal.devcards :as dc]
            [nextjournal.clerk.viewer :as v]
            [nextjournal.clerk.sci-viewer :as sv]
            [nextjournal.clerk.parser :as p]
            [shadow.resource :as rc]))

(def result-viewer
  (assoc v/result-block-viewer
         :transform-fn (comp v/mark-presented (v/update-val (comp sv/read-string :text)))
         :render-fn '(fn [form]
                       (try
                         (let [data (eval form)]
                           (if (v/valid-react-element? data) data (v/html [v/inspect data])))
                         (catch js/Error e
                           (v/html [:div.red (.-message e)]))))))

(defn doc->viewer [doc]
  (->> doc
       v/notebook
       (v/with-viewers (v/add-viewers [result-viewer]))
       v/present))

(defn notebook [doc]
  [sv/inspect-presented (doc->viewer doc)])

(dc/defcard app
  [notebook (p/parse-clojure-string {:doc? true} (rc/inline "hello_cljs.cljs"))])
