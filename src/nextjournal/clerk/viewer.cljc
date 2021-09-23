(ns nextjournal.clerk.viewer
  (:require [clojure.string :as str]
            [clojure.pprint :as pprint]
            [sci.impl.vars]
            [sci.impl.namespaces]
            #?(:cljs [reagent.ratom :as ratom])))

(defn wrap-value [x]
  (if (and (map? x) (:nextjournal/value x))
    x
    {:nextjournal/value x}))

#_(wrap-value 123)
#_(wrap-value {:nextjournal/value 456})

(defn wrapped-value? [x]
  (and (map? x)
       (contains? x :nextjournal/value)))


(defn value [x]
  (if (wrapped-value? x)
    (:nextjournal/value x)
    x))
#_(value (with-viewer '(+ 1 2 3) :eval!))
#_(value 123)

(defn with-viewer
  "The given viewer will be used to display data"
  [x viewer]
  (-> x
      wrap-value
      (assoc :nextjournal/viewer viewer)))

#_(with-viewer "x^2" :latex)

(defn with-viewers
  "Binds viewers to types, eg {:boolean view-fn}"
  [x viewers]
  (-> x
      wrap-value
      (assoc :nextjournal/viewers viewers)))

#_(-> "x^2" (with-viewer :latex) (with-viewers [{:name :latex :fn :mathjax}]))

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

(defn viewer [x]
  (when (map? x)
    (:nextjournal/viewer x)))

(defn code [x]
  (with-viewer (if (string? x) x (with-out-str (pprint/pprint x))) :code))

#_(viewer (with-viewer '(+ 1 2 3) :eval!))
#_(viewer "123")

(defn viewers [x]
  (when (map? x)
    (:nextjournal/viewers x)))

(def elide-string-length 100)

;; keep viewer selection stricly in Clojure
(def default-viewers
  ;; maybe make this a sorted-map
  [{:pred string? :fn '(fn [x] (v/html [:span.syntax-string.inspected-value "\"" x "\""])) :fetch-opts {:n elide-string-length}}
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
   {:pred uuid? :fn '(fn [x] (v/html (v/tagged-value "#uuid" [:span.syntax-string.inspected-value "\"" (str x) "\""])))}
   {:pred inst? :fn '(fn [x] (v/html (v/tagged-value "#inst" [:span.syntax-string.inspected-value "\"" (str x) "\""])))}])

(def named-viewers
  #{:html
    :hiccup
    :plotly
    :code
    :eval!
    :markdown
    :mathjax
    :latex
    :reagent
    :vega-lite
    :clerk/notebook
    :clerk/var
    :clerk/result})

(def preds->viewers
  (into {} (map (juxt :pred identity)) default-viewers))

(comment
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
        (assoc 1 long-string))))

(def elided
  :nextjournal/…)

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
       (or (wrapped-value? xs)
           (some->> xs viewer (contains? named-viewers))) xs
       (map-entry? xs) [(fetch (key xs) opts (conj current-path 0))
                        (fetch (val xs) opts (conj current-path 1))]
       (or (map? xs)
           (vector? xs)) (into (if (map? xs) [] (empty xs))
                               (comp (drop+take-xf opts)
                                     (map-indexed #(fetch %2 opts (conj current-path %1))))
                               (cond->> xs
                                 (and (map? xs) (not (sorted? xs))) (into (sorted-map))))
       (or (sequential? xs)
           (set? xs)) (sequence (comp (drop+take-xf opts)
                                      (map-indexed #(fetch %2 opts (conj current-path %1))))
                                (cond->> xs
                                  (and (set? xs) (not (sorted? xs))) (into (sorted-set))))
       (and (string? xs) (< elide-string-length (count xs))) (subs xs 0 n)
       :else xs))))

#_(fetch {1 2} {:n 10 :path []})
#_(fetch {[1 2 3]
          [4 [5 6 7] 8] 3 4} {:n 10 :path [0 1]})
#_(fetch '(1 2 (1 2 3) 4 5) {:n 10 :path [2]})
#_(fetch [1 2 [1 2 3] 4 5] {:n 10 :path [2]})
#_(fetch (range 200) {:n 20 :path [] :offset 60})
#_(fetch {[1] [1] [2] [2]} {:n 10})
#_(fetch [[1] [1]] {:n 10})
#_(fetch (plotly {:data [{:z [[1 2 3] [3 2 1]] :type "surface"}]}) {})

(defn select-viewer
  ([x] (select-viewer x default-viewers))
  ([x viewers]
   (if-let [selected-viewer (viewer x)]
     (cond (keyword? selected-viewer)
           (if (named-viewers selected-viewer)
             selected-viewer
             (throw (ex-info (str "cannot find viewer named " selected-viewer) {:selected-viewer selected-viewer :x (value x) :viewers viewers}))))
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
#_(select-viewer (md "# Hello"))
#_(select-viewer (html [:h1 "hi"]))

(defonce !viewers
  (#?(:clj atom :cljs ratom/atom) {:root default-viewers}))


(defn get-viewers
  ([scope] (get-viewers scope nil))
  ([scope viewers]
   (concat viewers (@!viewers scope) (@!viewers :root))))

(defn describe
  ([xs]
   (describe {:viewers (get-viewers *ns* (viewers xs))} xs))
  ([opts xs]
   (let [{:as opts :keys [viewers path]} (merge {:path []} opts)
         {:as viewer :keys [fetch-opts]} (try (select-viewer xs viewers)
                                              (catch #?(:clj Exception :cljs js/Error) _ex
                                                nil))
         xs (value xs)]
     #_(prn :xs xs :viewer viewer)
     (cond #_#_(and (empty? path) (nil? fetch-opts)) (cond-> {:path path} viewer (assoc :viewer viewer)) ;; fetch everything
           (map? xs) (let [children (sequence (comp (map-indexed #(describe (update opts :path conj %1) %2))
                                                    (remove (comp empty? :children))) xs)]
                       (cond-> {:count (count xs) :path path}
                         viewer (assoc :viewer viewer)
                         (seq children) (assoc :children children)))
           (counted? xs) (let [children (sequence (comp (map-indexed #(describe (update opts :path conj %1) %2))
                                                        (remove nil?))  xs)]
                           (cond-> {:path path :count (count xs) :viewer viewer}
                             (seq children) (assoc :children children)))
           ;; uncounted sequences assumed to be lazy
           (seq? xs) (let [{:keys [n]} fetch-opts
                           limit (+ n 10000)
                           count (bounded-count limit xs)
                           children (sequence (comp (drop+take-xf fetch-opts)
                                                    (map-indexed #(describe (update opts :path conj %1) %2))
                                                    (remove nil?)) xs)]
                       (cond-> {:path path :count count :viewer viewer}
                         (= count limit) (assoc :unbounded? true)
                         (seq children) (assoc :children children)))
           (and (string? xs) (< (:n fetch-opts 20) (count xs))) {:path path :count (count xs) :viewer viewer}
           :else nil))))

(describe {:viewers (get-viewers (find-ns 'rule-30-small))} (list (vector 1 2 3)))
(describe {:viewers [{:pred number?} {:pred vector?} {:pred list?}]} (list (vector 1 2 3)))

#_(describe complex-thing)
#_(describe {:one [1 2 3] 1 2 3 4})
#_(describe [1 2 [1 2 3] 4 5])
#_(describe (clojure.java.io/file "notebooks"))
#_(describe {:viewers [{:pred sequential? :fn pr-str}]} (range 100))
#_(describe (map vector (range)))
#_(describe (slurp "/usr/share/dict/words"))
#_(describe (plotly {:data [{:z [[1 2 3] [3 2 1]] :type "surface"}]}))
#_(describe (with-viewer [:h1 "hi"] :html))

(defn extract-info [{:as desc :keys [path]}]
  ;; TODO: drop `:fetch-opts` key once we read it from `:viewer` in frontend
  (-> desc
      (select-keys [:count :viewer])
      (assoc :fetch-opts (-> desc
                             (get-in [:viewer :fetch-opts])
                             (assoc :path path)))))

(defn path->info [desc]
  (into {} (map (juxt :path extract-info)) (tree-seq (some-fn sequential? map?) :children desc)))

#_(path->info (describe [1 [2] 3]))
#_(path->info (plotly {:data [{:z [[1 2 3] [3 2 1]] :type "surface"}]}))

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

#?(:clj
   (defn datafy-scope [scope]
     (cond
       (instance? clojure.lang.Namespace scope) {:namespace (-> scope str keyword)}
       (nil? scope) :root
       :else (throw (ex-info (str "Unsupported scope " scope) {:scope scope})))))

#_(datafy-scope *ns*)
#_(datafy-scope #'datafy-scope)

#?(:clj
   (defn set-viewers!- [scope viewers]
     (assert (or (#{:root} scope)
                 (instance? clojure.lang.Namespace scope)
                 (var? scope)))
     (swap! !viewers assoc scope (into [] (map #(update % :pred eval)) viewers))
     (with-viewer `'(v/set-viewers! ~(datafy-scope scope) ~viewers) :eval!)))


(defmacro set-viewers!
  ([viewers] (set-viewers!- *ns* viewers))
  ([scope viewers] (set-viewers!- scope viewers)))

#_(set-viewers! [])
#_(set-viewers! #'update-viewers! [])
#_(macroexpand '(set-viewers! []))

(defn registration? [x]
  (and (map? x) (contains? #{:eval!} (viewer x))))

#_(registration? (set-viewers! []))

#_(nextjournal.clerk/show! "notebooks/viewers/vega.clj")
