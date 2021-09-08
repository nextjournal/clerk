(ns nextjournal.clerk.viewer
  (:refer-clojure :exclude [meta with-meta vary-meta])
  (:require [clojure.core :as core]
            [clojure.string :as str]))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Custom metadata handling - supporting any cljs value - not compatible with core meta



(defn meta? [x] (and (map? x) (contains? x :nextjournal/value)))

(defn supports-meta? [x]
  #?(:cljs (satisfies? IWithMeta x)
     :clj (instance? clojure.lang.IMeta x)))

(defn meta [data]
  (if (meta? data)
    data
    (assoc (core/meta data)
           :nextjournal/value (cond-> data
                                (supports-meta? data) (core/with-meta {})))))

(defn with-meta [data m]
  (cond (meta? data) (assoc m :nextjournal/value (:nextjournal/value data))
        (supports-meta? data) (core/with-meta data m)
        :else
        (assoc m :nextjournal/value data)))

#_(with-meta {:hello :world} {:foo :bar})
#_(with-meta "foo" {:foo :bar})

(defn vary-meta [data f & args]
  (with-meta data (apply f (meta data) args)))


;; - a name
;; - a predicate function
;; - a view function
;; - ordering!
(declare view-as)



#_
(view-as '#(v/html [:div.inline-block {:style {:width 16 :height 16}
                                       :class (if (pos? %) "bg-black" "bg-white border-solid border-2 border-
black")}]) 1)

;; keep viewer selection stricly in Clojure
(def default-viewers
  ;; maybe make this a sorted-map
  [{:pred string? :fn '(fn [x] (v/html [:span.syntax-string.inspected-value "\"" x "\""]))}
   {:pred number? :fn '(fn [x] (v/html [:span.syntax-number.inspected-value
                                        (if (js/Number.isNaN x) "NaN" (str x))]))}
   {:pred symbol? :fn '(fn [x] (v/html [:span.syntax-symbol.inspected-value x]))}
   {:pred keyword? :fn '(fn [x] (v/html [:span.syntax-keyword.inspected-value (str x)]))}
   {:pred nil? :fn '(fn [_] (v/html [:span.syntax-nil.inspected-value "nil"]))}
   {:pred boolean? :fn '(fn [x] (v/html [:span.syntax-bool.inspected-value (str x)]))}
   {:pred fn? :fn '(fn [_] (v/html [:span.inspected-value [:span.syntax-tag "ƒ"] "()"]))}
   {:pred vector? :fn '(partial v/coll-viewer {:open "[" :close "]"}) :fetch-opts {:n 20}}
   {:pred set? :fn '(partial v/coll-viewer {:open "#{" :close "}"}) :fetch-opts {:n 20}}
   {:pred (some-fn list? sequential?) :fn '(partial v/coll-viewer {:open "(" :close ")"})  :fetch-opts {:n 20}}
   {:pred map? :fn '(partial v/map-viewer) :fetch-opts {:n 20}}
   {:pred uuid? :fn '(fn [x] (v/html (v/tagged-value "#uuid" ['nextjournal.clerk.sci-viewer/inspect (str x)])))}
   {:pred inst? :fn '(fn [x] (v/html (v/tagged-value "#inst" ['nextjournal.clerk.sci-viewer/inspect (str x)])))}])

(def preds->viewers
  (into {} (map (juxt :pred identity)) default-viewers))

(def map-100
  (zipmap (range 100) (range 100)))

(def dict-words
  (vec (take 100 #?(:cljs (range) :clj (str/split-lines (slurp "/usr/share/dict/words"))))))

(def long-string
  (str/join dict-words))

(def complex-thing
  (-> (zipmap (range 100) (range 100))
      (assoc 0 dict-words)
      (assoc :words dict-words)
      (assoc-in [:map :deep] (zipmap (range 100) (range 100)))
      (assoc 1 long-string)))

(def elided
  :nextjournal/…)

(def elide-string-length 20)

(defn drop+take-xf [{:keys [n offset]
                     :or {offset 0}}]
  (assert (pos-int? n) "n must be a positive integer")
  (comp (drop offset)
        (take n)))

#_(sequence (drop+take-xf {:n 10}) (range 100))
#_(sequence (drop+take-xf {:n 10 :offset 10}) (range 100))
(defn fetch
  ([xs opts]
   #_(prn :start-fetch xs :opts opts)
   (fetch xs opts []))
  ([xs {:as opts :keys [path n]} current-path]
   #_(prn :xs xs :opts opts :current-path current-path)
   (if (< (count current-path)
          (count path))
     (let [idx (first (drop (count current-path) path))]
       #_(prn :idx idx)
       (fetch (cond (map? xs) (nth (seq xs) idx)
                    (associative? xs) (get xs idx)
                    (sequential? xs) (nth xs idx))
              opts
              (conj current-path idx)))
     (cond
       (and (not= (count path)
                  (count current-path))
            (not (number? xs))
            (or (associative? xs)
                (sequential? xs)
                (and (string? xs) (< elide-string-length (count xs))))) elided
       (or (map? xs)
           (vector? xs)) (into (empty xs) (comp (drop+take-xf opts) (map-indexed #(fetch %2 opts (conj current-path %1)))) xs)
       (sequential? xs) (sequence (comp (drop+take-xf opts) (map-indexed #(fetch %2 opts (conj current-path %1)))) xs)
       (and (string? xs) (< elide-string-length (count xs))) (subs xs 0 n)
       :else xs))))


#_(fetch {1 2} {:n 10 :path []})
#_(fetch {[1 2 3]
          [4 [5 6 7] 8] 3 4} {:n 10 :path [0 1]})
#_(fetch '(1 2 (1 2 3) 4 5) {:n 10 :path [2]})
#_(fetch [1 2 [1 2 3] 4 5] {:n 10 :path [2]})
#_(fetch (range 200) {:n 20 :path [] :offset 60})


(let [xs {1 2}
      opts {:n 10}
      current-path []]
  #_
  (into {} (comp (drop+take-xf opts) (map-indexed #(fetch opts (conj current-path %1) %2))) xs)
  #_#_into [] (map-indexed #(fetch opts (conj current-path %1) %2) xs))

(defn select-viewer
  ([x] (select-viewer x default-viewers))
  ([x viewers]
   (if-let [selected-viewer (-> x meta :nextjournal/viewer)]
     (let [x (:nextjournal/value x x)]
       (cond (keyword? selected-viewer)
             (if-let [viewer (get (into {} (map (juxt :name identity)) viewers) selected-viewer)]
               viewer
               (throw (ex-info (str "cannot find viewer named " selected-viewer) {:selected-viewer selected-viewer :x x :viewers viewers})))))
     (loop [v viewers]
       (if-let [{:as matching-viewer :keys [pred]} (first v)]
         (if (and pred (pred x))
           matching-viewer
           (recur (rest v)))
         (throw (ex-info (str "cannot find matchting viewer") {:viewers viewers :x x})))))))


#_(select-viewer {:one :two})
#_(select-viewer [1 2 3])
#_(select-viewer (range 3))

(def n 20)


(defn describe
  ([xs]
   (describe [] xs))
  ([path xs]
   (let [viewer (select-viewer xs)]
     (cond (map? xs) (let [children (remove (comp empty? :children)
                                            (map #(describe (conj path %1) %2) (range) xs))]
                       (cond-> {:count (count xs)
                                :path path
                                :viewer viewer}
                         (seq children) (assoc :children children)))
           (counted? xs) (let [children (remove nil? (map #(describe (conj path %1) %2) (range) xs))]
                           (cond-> {:path path
                                    :count (count xs)
                                    :viewer viewer}
                             (seq children) (assoc :children children)))
           (and (string? xs) (< n (count xs))) {:path path :count (count xs) :viewer viewer}
           :else nil))))

(select-viewer (range 10))

#_(describe complex-thing)
#_(describe {:one [1 2 3] 1 2 3 4})
#_(describe [1 2 [1 2 3] 4 5])

;; maybe sort maps


(comment
  (let [x complex-thing
        {:as desc :keys [path]} (describe x)]
    (fetch x {:path [0 1] :n 20})))

;; request first 20 map elements
{:count 20
 :offset 0
 :path []}

{:path [0 1]
 :count 20
 :offset 0}


;; Homework
;; - make `fetch` symmetric to `describe` with `path`
;; - make `fetch` work with `path->opts`
;; - use `path` as a react key on the

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

;; TODO: hack for talk to make sql result display as table, propery support SQL results as tables and remove
(defn ->table
  "converts a sequence of maps into a table with the first row containing the column names."
  [xs]
  (let [cols (sort (keys (first xs)))]
    (into [cols]
          (map (fn [row] (map #(get row %) cols)))
          xs)))

#_(->table [{:a 1 :b 2 :c 3} {:a 3 :b 0 :c 2}])

(defn table [xs]
  (view-as :table (->table xs)))

(defmacro register-viewers! [v]
  `(nextjournal.clerk.viewer/with-viewer
     ::register!
     (quote (let [viewers# ~v]
              (nextjournal.viewer/register-viewers! viewers#)
              (constantly viewers#)))))

#_
(macroexpand-1 (register-viewers! {:vector (fn [x options]
                                             (html (into [:div.flex.inline-flex] (map (partial inspect options)) x)))}))


(defn registration? [x]
  (boolean (-> x meta :nextjournal/value #{::register!})))

(defn ^:deprecated paginate [x]
  x)
