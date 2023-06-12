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
           :scroll-to-anchor? false
           :emoji emoji
           :path (clerk/doc-url file (:id attrs))
           :items (md-toc->navbar-items file item)}) children))

(defn meta-toc [paths]
  (into []
        (mapcat (comp (fn [{:keys [toc file]}] (md-toc->navbar-items file toc))
                      (partial parser/parse-file {:doc? true})))
        paths))

(def book-viewer
  (update v/notebook-viewer
          :transform-fn (fn [original-transform]
                          (fn [wrapped-value]
                            (println :doc (:file (v/->value wrapped-value)))
                            (-> wrapped-value
                                original-transform
                                (assoc :nextjournal/opts {:expandable? true})
                                (assoc-in [:nextjournal/value :toc]
                                          (meta-toc notebooks)))))))

#_
(clerk/add-viewers! [book-viewer])

;; Test actuall cross-doc toc

(clerk/reset-viewers! :default
                      (clerk/add-viewers (clerk/get-default-viewers)
                                         [book-viewer]))
