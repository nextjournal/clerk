(ns nextjournal.clerk.viewer
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
  [{:pred map? :fetch-opts {:n 3}}

   {:pred string?}
   {:pred number?}
   {:pred symbol?}
   {:pred keyword?}
   {:pred nil?}
   {:pred boolean?}
   {:pred fn?}
   {:pred vector? :fetch-opts {:n 20}}
   {:pred list? :fetch-opts {:n 20}}
   {:pred set? :fetch-opts {:n 20}}
   {:pred map? :fetch-opts {:n 20}}
   {:pred uuid?}
   {:pred inst?}])

(def preds->viewers
  (into {} (map (juxt :pred identity)) default-viewers))

(def map-100
  (zipmap (range 100) (range 100)))

(def dict-words
  (vec (take 100 (clojure.string/split-lines (slurp "/usr/share/dict/words")))))

(def long-string
  (clojure.string/join dict-words))

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
  (comp (drop offset)
        (take n)))

#_(sequence (drop+take-xf {:n 10}) (range 100))
#_(sequence (drop+take-xf {:n 10 :offset 10}) (range 100))

(do
  (defn fetch
    ([opts xs]
     (fetch opts [] xs))
    ([{:as opts :keys [path n]} current-path xs]
     #_(prn :current-path current-path :path path :xs xs)
     (if (< (count current-path)
            (count path))
       (let [idx (first (drop (count current-path) path))]
         #_(prn :idx idx)
         (fetch opts
                (conj current-path idx)
                (cond (map? xs) (nth (seq xs) idx)
                      (associative? xs) (get xs idx)
                      (sequential? xs) (nth xs idx))))
       (cond
         (and (not= (count path)
                    (count current-path))
              (not (map-entry? xs))
              (or (associative? xs)
                  (sequential? xs)
                  (and (string? xs) (< elide-string-length (count xs))))) elided
         (map? xs) (into {} (comp (drop+take-xf opts) (map-indexed #(fetch opts (conj current-path %1) %2))) xs)
         ;; TODO: debug why error reportig is broken when removing the following line
         (vector? xs) (into [] (comp (drop+take-xf opts) (map-indexed #(fetch opts (conj current-path %1) %2))) xs)
         (sequential? xs) (sequence (comp (drop+take-xf opts) (map-indexed #(fetch opts (conj current-path %1) %2))) xs)
         (and (string? xs) (< elide-string-length (count xs))) (subs xs 0 n)
         :else xs))))

  (fetch {:n 10 :path []} {1 2})
  #_(fetch {:n 10 :path [0]} {[1 2 3]
                              [4 [5 6 7] 8] 3 4})
  #_(fetch {:n 10 :path [2]} '(1 2 (1 2 3) 4 5))
  #_(fetch {:n 10 :path [2]} [1 2 [1 2 3] 4 5]))


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


(def n 20)

(do

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

  (describe complex-thing)
  #_(describe {:one [1 2 3] 1 2 3 4})
  #_(describe [1 2 [1 2 3] 4 5]))

;; maybe sort maps


(let [x complex-thing
      {:as desc :keys [path]} (describe x)]
  (fetch {:path [0 1] :n 20} x))

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
;; - use `path` as a react key on the frontend

(defn type-key [value]
  (cond
    (var? value) :var
    (instance? clojure.lang.IDeref value) :derefable
    (map? value) :map
    (set? value) :set
    (vector? value) :vector
    (list? value) :list
    (seq? value) :list
    (fn? value) :fn
    (uuid? value) :uuid
    (string? value) :string
    (keyword? value) :keyword
    (symbol? value) :symbol
    (nil? value) :nil
    (boolean? value) :boolean
    (inst? value) :inst
    :else :untyped))

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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Pagination + Lazy Loading
(defn describe [result]
  (cond-> {:nextjournal/type-key (type-key result) :blob/id (-> result core/meta :blob/id)}
    (counted? result)
    (assoc :count (count result))))

#_(describe (vec (range 100)))

(defn paginate [result {:as opts :keys [start n] :or {start 0}}]
  (if (and (number? n)
           (pos? n)
           (not (or (map? result)
                    (set? result)))
           (counted? result))
    (core/with-meta (->> result (drop start) (take n) doall) (merge opts (describe result)))
    result))

#_(meta (paginate (vec (range 10)) {:n 20}))
#_(meta (paginate (vec (range 100)) {:n 20}))
#_(meta (paginate (zipmap (range 100) (range 100)) {:n 20}))
#_(paginate #{1 2 3} {:n 20})
