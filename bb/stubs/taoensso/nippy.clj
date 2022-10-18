(ns taoensso.nippy)

(defn freezable? [x] false)
(defn freeze [x] x)
(defn thaw-from-file [file] "thawed")
