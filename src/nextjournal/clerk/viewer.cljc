(ns nextjournal.clerk.viewer
  (:require [clojure.string :as str]
            [sci.impl.vars]
            [sci.impl.namespaces]
            #?(:cljs [reagent.ratom :as ratom])))

(defn value [x]
  (if (map? x)
    (:nextjournal/value x x)
    x))

#_(value (with-viewer '(+ 1 2 3) :eval!))
#_(value 123)

(defn viewer [x]
  (when (map? x)
    (:nextjournal/viewer x)))

#_(viewer (with-viewer '(+ 1 2 3) :eval!))
#_(viewer "123")

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
  (cond-> (drop offset)
    (pos-int? n)
    (comp (take n))))

#_(sequence (drop+take-xf {:n 10}) (range 100))
#_(sequence (drop+take-xf {:n 10 :offset 10}) (range 100))
#_(sequence (drop+take-xf {}) (range 9))

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
            (not (or (number? xs)
                     (map-entry? xs)))
            (or (associative? xs)
                (sequential? xs)
                (and (string? xs) (< elide-string-length (count xs))))) elided
       (map-entry? xs) [(fetch (key xs) opts (conj current-path 0))
                        (fetch (val xs) opts (conj current-path 1))]
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
#_(fetch {[1] [1] [2] [2]} {:n 10})

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
#_(select-viewer (clojure.java.io/file "notebooks"))

;; TODO: remove
(def n 20)

;; TODO:
;; - sort maps if possible
;; - handle lazy seqs
(defn describe
  ([xs]
   (describe {} xs))
  ([opts xs]
   (let [{:keys [viewers path]} (merge {:viewers default-viewers :path []} opts)
         viewer (try (select-viewer xs viewers)
                     (catch #?(:clj Exception :cljs js/Error) _ex
                       nil))]
     (cond (map? xs) (let [children (remove (comp empty? :children)
                                            (map #(describe (update opts :path conj %1) %2) (range) xs))]
                       (cond-> {:count (count xs)
                                :path path}
                         viewer (assoc :viewer viewer)
                         (seq children) (assoc :children children)))
           (counted? xs) (let [children (remove nil? (map #(describe (update opts :path conj %1) %2) (range) xs))]
                           (cond-> {:path path
                                    :count (count xs)
                                    :viewer viewer}
                             (seq children) (assoc :children children)))
           (and (string? xs) (< n (count xs))) {:path path :count (count xs) :viewer viewer}
           :else nil))))

#_(describe complex-thing)
#_(describe {:one [1 2 3] 1 2 3 4})
#_(describe [1 2 [1 2 3] 4 5])
#_(describe (clojure.java.io/file "notebooks"))
#_(describe {:viewers [{:pred sequential? :fn pr-str}]} (range 100))

(defn extract-info [{:as desc :keys [path]}]
  (-> desc
      (select-keys [:count])
      (assoc :fetch-opts (-> desc
                             (get-in [:viewer :fetch-opts])
                             (assoc :path path)))))

(defn path->info [desc]
  (into {} (map (juxt :path extract-info)) (tree-seq (some-fn sequential? map?) :children desc)))

#_(path->info (describe [1 [2] 3]))

(defn with-viewer
  "The given viewer will be used to display data"
  [x viewer]
  {:nextjournal/viewer viewer :nextjournal/value x})

(defn with-viewers
  "Binds viewers to types, eg {:boolean view-fn}"
  [x viewers]
  {:nextjournal/viewers viewers :nextjournal/value x})

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

(defonce !viewers
  (#?(:clj atom :cljs ratom/atom) {:root default-viewers}))

(defn update-viewers! [scope viewers]
  (swap! !viewers assoc scope (vec (concat viewers default-viewers))))

#?(:clj
   (defn datafy-scope [scope]
     (cond
       (instance? clojure.lang.Namespace scope) {:namespace (str scope)}
       (instance? clojure.lang.Namespace scope) {:var (str scope)}
       :else :root)))

#?(:clj
   (defn set-viewers!- [scope viewers]
     (assert (or (#{:root} scope)
                 (instance? clojure.lang.Namespace scope)
                 (instance? clojure.lang.Var scope)))
     (update-viewers! scope (into [] (map #(update % :pred eval)) viewers))
     (with-viewer `'(v/set-viewers! ~(datafy-scope scope) ~viewers) :eval!)))

#?(:clj
   (defn get-viewers [scope]
     (or (@!viewers scope)
         (@!viewers :root))))

(defmacro set-viewers!
  ([viewers] (set-viewers!- *ns* viewers))
  ([var viewers] (set-viewers!- var viewers)))

#_(set-viewers! [])
#_(set-viewers! #'update-viewers! [])
#_(macroexpand '(set-viewers! []))

(defn registration? [x]
  (and (map? x) (contains? #{:eval!} (viewer x))))

#_(registration? (set-viewers! []))
