(ns nextjournal.viewer
  (:refer-clojure :exclude [meta with-meta vary-meta])
  (:require [clojure.core :as core]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Custom metadata handling - supporting any cljs value - not compatible with core meta

(defn meta? [x] (and (map? x) (contains? x :nextjournal/value)))

(defn meta [data]
  (if (meta? data)
    data
    (assoc (core/meta data)
           :nextjournal/value (cond-> data
                                (instance? clojure.lang.IMeta data) (core/with-meta {})))))

(defn with-meta [data m]
  (cond (meta? data) (assoc m :nextjournal/value (:nextjournal/value data))
        (instance? clojure.lang.IMeta data) (core/with-meta data m)
        :else
        (assoc m :nextjournal/value data)))

#_(with-meta {:hello :world} {:foo :bar})
#_(with-meta "foo" {:foo :bar})

(defn vary-meta [data f & args]
  (with-meta data (apply f (meta data) args)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Viewers (built on metadata)

(defn with-viewer
  "The given viewer will be used to display data"
  [data viewer]
  (vary-meta data assoc :nextjournal/viewer viewer))

(defn with-viewers
  "Binds viewers to types, eg {:boolean view-fn}"
  [data viewers]
  (vary-meta data assoc :nextjournal/viewers viewers))

(defn view-as
  "Like `with-viewer` but takes viewer as 1st argument"
  [viewer data]
  (with-viewer data viewer))

#_(view-as :latex "a^2+b^2=c^2")

(defn html [x]
  (with-viewer x (if (string? x) :html :hiccup)))

(defn vl [x]
  (with-viewer x :vega-lite))

(defn plotly [x]
  (with-viewer x :plotly))

(defn md [x]
  (with-viewer x :markdown))

(defn tex [x]
  (with-viewer x :latex))

(defn table [xs]
  (view-as :table
           (into []
                 (map #(into {} %))
                 xs)))

(defmacro register-viewers! [v]
  `(with-viewer
     ::register!
     (quote (let [viewers# ~v]
              (nextjournal.viewer/register-viewers! viewers#)
              (constantly viewers#)))))

#_
(macroexpand-1 (register-viewers! {:vector (fn [x options]
                                             (html (into [:div.flex.inline-flex] (map (partial inspect options)) x)))}))
