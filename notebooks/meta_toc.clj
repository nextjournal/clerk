;; # 📕 Meta Table of Contents
(ns meta-toc
  {:nextjournal.clerk/toc true}
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.clerk.parser :as parser]
            [nextjournal.markdown.transform :as md.transform]
            [nextjournal.clerk.viewer :as v]))

;; This assembles the table of contents programmatically from a
;; collection of notebooks.

;; ## Notebooks
(def notebooks
  ["notebooks/how_clerk_works.clj"
   "notebooks/cherry.clj"
   "notebooks/tracer.clj"
   "notebooks/document_linking.clj"])

(defn md-toc->navbar-items [current-notebook file {:keys [children]}]
  (mapv (fn [{:as item :keys [emoji attrs]}]
          {:title (md.transform/->text item)
           :expanded? (= current-notebook file)
           :scroll-to-anchor? false
           :emoji emoji
           :path (clerk/doc-url file (:id attrs))
           :items (md-toc->navbar-items current-notebook file item)}) children))

(defn meta-toc [current-notebook paths]
  (into []
        (mapcat (comp (fn [{:keys [toc file]}] (md-toc->navbar-items current-notebook file toc))
                      (partial parser/parse-file {:doc? true})))
        paths))

(def book-viewer
  (update v/notebook-viewer
          :transform-fn (fn [original-transform]
                          (fn [wrapped-value]
                            (-> wrapped-value
                                original-transform
                                (assoc :nextjournal/opts {:expandable? true})
                                (assoc-in [:nextjournal/value :toc]
                                          (meta-toc (:file (v/->value wrapped-value)) notebooks)))))))

#_(clerk/add-viewers! [book-viewer])

;; Test actual cross-doc toc
(clerk/add-viewers! [book-viewer])
