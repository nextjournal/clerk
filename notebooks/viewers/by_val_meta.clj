;; # Viewer Selection by Value Metadata
(ns viewers.by-val-meta
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as viewer]))

;; We can support selecting a viewer based on the metadata of a
;; value. We do this with a `:pred` function that checks the for
;; `::clerk/viewer` in the metadata. Then we use this viewer in the
;; `:transform-fn` as an arg to `clerk/with-viewer`.

(def viewer-by-val-meta-viewer
  {:pred (fn [x] (-> x meta ::clerk/viewer ifn?))
   :transform-fn (fn [{:as wrapped-value :nextjournal/keys [value]}]
                   (let [{:as metadata :nextjournal.clerk/keys [viewer]} (meta value)]
                     (clerk/with-viewer viewer metadata wrapped-value)))})

(clerk/add-viewers! [viewer-by-val-meta-viewer])

;; Let's try this out using a viewer convenience function.
(with-meta [:h1 "Hello 👋"] {::clerk/viewer clerk/html})

;; Or using the viewer map and setting the width to `:wide`:
(with-meta [:h1 "Hello, again! 👋"] {::clerk/viewer nextjournal.clerk.viewer/html-viewer
                                     ::clerk/width :wide})
