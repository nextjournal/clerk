(ns errors
  (:require [nextjournal.clerk :as clerk]))

(clerk/with-viewer {:render-fn '(fn [x] boom)}
  42)

(deftype FooBar [])

(defmethod print-method FooBar [x w] (.write w "foo/bar/unreadable"))

(FooBar.)
