(ns nextjournal.clerk.render.localstorage
  (:require [cljs.reader]))

(defn set-item! [key val]
  (when (exists? js/window)
    (.setItem (.-localStorage js/window) key val)))

(defn get-item [key]
  (when (exists? js/window)
    (cljs.reader/read-string (.getItem (.-localStorage js/window) key))))

(defn remove-item! [key]
  (when (exists? js/window)
    (.removeItem (.-localStorage js/window) key)))

