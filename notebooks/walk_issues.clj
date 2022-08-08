;; # Walk Issues
(ns walk-issues
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as v])
  (:import (clojure.lang IPersistentCollection Seqable)))

;; ## A persistent collection without seq

(deftype UnWalkable1 []
  IPersistentCollection)

;; Passes `seqable?` check, crashes `n.c.analyzer/exceeds-bounded-count-limit?` calling `(tree-seq seqable? seq x)` on it.

(->UnWalkable1)

;; ## A persistent collection with seq and an empty value but no cons

(deftype UnWalkable2 [m]
  IPersistentCollection
  (empty [_this] (->UnWalkable2 {}))

  Seqable
  (seq [_this] (seq m)))

(comment
  ;; this cannot be walked because of the missing cons on an empty instance of the type
  (clojure.walk/postwalk identity (->UnWalkable2 {:a 1})))

;; doesn't fail to be shown as is (the fallback viewer kicks in)

(->UnWalkable2 {:a 1})

;; but fails if some seqable structure is presented with some unwalkable value inside

(v/with-viewer {:transform-fn v/mark-presented}
  {:a (->UnWalkable2 {:b 1})})

;; `n.c.viewer/extract-blobs` calls a postwalk on the presented value
