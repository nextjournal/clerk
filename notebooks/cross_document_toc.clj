;; # 📕 Cross-Document Table of Contents
(ns cross-document-toc
  {:nextjournal.clerk/toc true }
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.clerk.parser :as parser]
            [nextjournal.markdown.transform :as md.transform]
            [nextjournal.clerk.viewer :as v]))

;; ## Notebooks
(def notebooks
  ["notebooks/how_clerk_works.clj"
   "notebooks/cherry.clj"
   "notebooks/tracer.clj"
   "notebooks/document_linking.clj"])

(defn md-toc->navbar-items [file {:keys [children]}]
  (mapv (fn [{:as item :keys [emoji attrs]}]
          {:title (md.transform/->text item)
           :expanded? true
           :emoji emoji
           :path (clerk/doc-url file (:id attrs))
           :items (md-toc->navbar-items file item)}) children))

(defn meta-toc [paths]
  (into []
        (mapcat (comp (fn [{:keys [toc file]}] (md-toc->navbar-items file toc))
                      (partial parser/parse-file {:doc? true})))
        paths))

(clerk/add-viewers! [(update v/notebook-viewer
                             :transform-fn (partial comp
                                                    (fn [doc] (assoc-in doc [:nextjournal/value :toc]
                                                                        (meta-toc notebooks)))))])
