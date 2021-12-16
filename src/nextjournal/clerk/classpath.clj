(ns nextjournal.clerk.classpath
  (:require [clojure.java.classpath :as cp])
  (:import (java.io File)
           (java.util.jar JarFile)))


(defn classpath-directories
  "Like `clojure.java.classpath/classpath-directories` but using the `system-classpath` which cider doesn't break,
  see https://github.com/clojure-emacs/cider-nrepl/pull/668"
  []
  (filter #(.isDirectory ^File %) (cp/system-classpath)))


(defn classpath-jarfiles
  "Like `clojure.java.classpath/classpath-jarfiles` but using the `system-classpath` which cider doesn't break,
  see https://github.com/clojure-emacs/cider-nrepl/pull/668"
  []
  (map #(JarFile. ^File %) (filter cp/jar-file? (cp/system-classpath))))
