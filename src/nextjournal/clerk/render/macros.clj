(ns nextjournal.clerk.render.macros
  (:require [clojure.string :as str]
            [nextjournal.clerk.analyzer :as analyzer]
            [nextjournal.clerk.parser :as parser]
            [nextjournal.clerk.webserver :as webserver]))

(defmacro sci-copy-nss [& nss]
  (into {} (for [[_ ns] nss]
             [(list 'quote ns) `(sci.core/copy-ns ~ns (sci.core/create-ns '~ns))])))

#_(macroexpand '(sci-copy-nss 'nextjournal.clerk.render.hoooks
                              'nextjournal.clerk.render.code))

(defn cljs-source [file]
  (-> (parser/parse-file {:doc? true} file)
      analyzer/analyze-doc
      (dissoc :->analysis-info :graph :ns)
      (update :blocks (fn [blocks] (mapv (fn [{:as block :keys [form]}]
                                           (cond-> (-> block
                                                       (update :form #(list 'quote %))
                                                       (update :id #(list 'quote %)))
                                             form (assoc :result {:nextjournal/value form})))
                                         blocks)))))

#_(cljs-source "notebooks/clojurescript.cljs")

(defmacro render-cljs-when-set []
  (when-let [file (:file @webserver/!doc)]
    (when (str/ends-with? file ".cljs")
      (list 'nextjournal.clerk.viewer/notebook (cljs-source file)))))

#_(reset! webserver/!doc {:file "notebooks/clojurescript.cljs"})

