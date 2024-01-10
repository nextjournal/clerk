;; # 📕 Meta Table of Contents
(ns meta-toc
  {:nextjournal.clerk/toc true}
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.clerk.parser :as parser]
            [nextjournal.clerk.viewer :as viewer]
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
          {:title (cond-> (md.transform/->text item) (seq emoji) (subs (count emoji)))
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

(def toc-viewer
  (update v/toc-viewer
          :transform-fn
          (fn [original-transform]
            (fn transform-fn [{:as wrapped-value doc :nextjournal/value}]
              (-> wrapped-value
                  original-transform
                  (assoc :nextjournal/render-opts {:expandable-toc? true})
                  (assoc :nextjournal/value (meta-toc (:file doc) notebooks)))))))

(clerk/add-viewers! [toc-viewer])

(comment
  ;; Test actual cross-doc toc
  (viewer/reset-viewers! :default (viewer/add-viewers [toc-viewer]))
  (viewer/reset-viewers! :default (viewer/get-default-viewers))
  (clerk/build! {:render-router :fetch-edn :paths notebooks}))
