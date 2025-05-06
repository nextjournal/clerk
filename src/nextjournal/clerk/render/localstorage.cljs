(ns nextjournal.clerk.render.localstorage
  (:require [clojure.edn :as edn]))

(defn set-item! [key val]
  (when (exists? js/window)
    (.setItem (.-localStorage js/window) key val)))

(defn get-item [key]
  (when (exists? js/window)
    (edn/read-string (.getItem (.-localStorage js/window) key))))

(defn remove-item! [key]
  (when (exists? js/window)
    (.removeItem (.-localStorage js/window) key)))

