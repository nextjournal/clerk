(ns babashka
  (:require [babashka.fs :as fs]))

(System/getProperty "babashka.version")

*file*

(fs/exists? *file*)

;;;;

;;;; dude

(+ 1 2 3)

(prn :dude)

java.io.File

(prn :halloe)
