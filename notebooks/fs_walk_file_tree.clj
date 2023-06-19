(ns fs-walk-file-tree
  (:require [babashka.fs :as fs])
  (:import [java.nio.file FileSystems]))

(set! *warn-on-reflection* true)

(time
 (let [globs (repeat 47 ["**.clj"])
       matchers (mapv (fn [glob]
                        (.getPathMatcher
                         (FileSystems/getDefault)
                         (str "glob:" glob)))
                      globs)
       results (atom [])
       matches? (fn [^java.nio.file.PathMatcher matcher path]
                  (when (.matches matcher path)
                    (swap! results conj path))
                  nil)]
   (fs/walk-file-tree "." {:visit-file (fn [path _]
                                         (doseq [matcher matchers]
                                           (matches? matcher path))
                                         :continue)})
   @results))

