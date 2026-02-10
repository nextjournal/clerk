(ns require-cljs-test
  (:require [nextjournal.clerk :as clerk]
            [viewers.viewer-with-cljs-source :as wcs]))

;; This notebook tests that:
;; - [x] You can load a viewer in a notebook that uses :require-cljs without using :require-cljs itself in the notebook
;; - [x] The used viewer can have a transitive .cljs or .cljc dependency

^{::clerk/no-cache true}
(clerk/with-viewer wcs/my-cool-viewer
  [1 2 3 4])
