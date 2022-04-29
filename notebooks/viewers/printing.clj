(ns viewers.printing
  (:require [clojure.datafy :as datafy]
            [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as v]))

#_(clerk/with-viewer v/default-viewers #'inc)
(clerk/set-viewers! v/default-viewers)

(atom {:foo (range 100000)})

(v/viewer-for v/default-viewers (datafy/datafy (java.io.File. "/Users/mk/dev/blog")))

(java.io.File. "/Users/mk/dev/blog")

#'inc

(clojure.datafy/datafy #'inc)

(find-ns 'nextjournal.clerk)
