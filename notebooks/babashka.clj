(ns babashka
  ; {:nextjournal.clerk/no-cache true}
  (:require [babashka.fs :as fs]
            [nextjournal.clerk :as clerk]))

(System/getProperty "babashka.version")

*file*

(fs/exists? *file*)

;;;;

;;;; dude

(+ 1 2 3)

(prn :dude)

java.io.File

(def x 2)

(prn x)

(clerk/with-viewer
  {:render-fn '(fn [_]
                 [:div {:style {:color "green"}}
                  "Hello"])}
  nil)
