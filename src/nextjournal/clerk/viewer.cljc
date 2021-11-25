(ns nextjournal.clerk.viewer
  (:require [clojure.string :as str]
            [clojure.pprint :as pprint]
            #?@(:clj [[clojure.repl :refer [demunge]]
                      [nextjournal.clerk.config :as config]]
                :cljs [[reagent.ratom :as ratom]]))
  #?(:clj (:import [clojure.lang IFn])))

(defrecord Form [form])

(defrecord Fn+Form [form fn]
  IFn
  (#?(:clj invoke :cljs -invoke) [this x]
    ((:fn this) x))
  (#?(:clj invoke :cljs -invoke) [this x y]
    ((:fn this) x y)))


(defn fn+form? [x]
  (instance? Fn+Form x))

(defn form->fn+form
  ([form]
   (map->Fn+Form {:form form :fn (#?(:clj eval :cljs *eval*) form)})))

#?(:clj
   (defmethod print-method Fn+Form [v ^java.io.Writer w]
     (.write w (str "#function+ " (pr-str `~(:form v))))))

#?(:clj
   (defmethod print-method Form [v ^java.io.Writer w]
     (.write w (str "#function+ " (pr-str `~(:form v))))))

#_(binding [*data-readers* {'function+ form->fn+form}]
    (read-string (pr-str (form->fn+form '(fn [x] x)))))
#_(binding [*data-readers* {'function+ form->fn+form}]
    (read-string (pr-str (form->fn+form 'number?))))

(comment
  (def num? (form->fn+form 'number?))
  (num? 42)
  (:form num?)
  (pr-str num?))

;; TODO: think about naming this to indicate it does nothing if the value is already wrapped.
(defn wrap-value
  "Ensures `x` is wrapped in a map under a `:nextjournal/value` key."
  ([x] (if (and (map? x) (:nextjournal/value x)) x {:nextjournal/value x}))
  ([x v] (-> x wrap-value (assoc :nextjournal/viewer v))))

#_(wrap-value 123)
#_(wrap-value {:nextjournal/value 456})

(defn wrapped-value?
  "Tests if `x` is a map containing a `:nextjournal/value`."
  [x]
  (and (map? x)
       (contains? x :nextjournal/value)))


(defn value
  "Takes `x` and returns the `:nextjournal/value` from it, or otherwise `x` unmodified."
  [x]
  (if (wrapped-value? x)
    (:nextjournal/value x)
    x))

#_(value (with-viewer :code '(+ 1 2 3)))
#_(value 123)

(defn viewer
  "Returns the `:nextjournal/viewer` for a given wrapped value `x`, `nil` otherwise."
  [x]
  (when (map? x)
    (:nextjournal/viewer x)))


#_(viewer (with-viewer :code '(+ 1 2 3)))
#_(viewer "123")

(defn viewers
  "Returns the `:nextjournal/viewers` for a given wrapped value `x`, `nil` otherwise."
  [x]
  (when (wrapped-value? x)
    (:nextjournal/viewers x)))

(defn width
  "Returns the `:nextjournal/width` for a given wrapped value `x`, `nil` otherwise."
  [x]
  (when (wrapped-value? x)
    (:nextjournal/width x)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; table viewer normalization

(defn rpad-vec [v length padding]
  (vec (take length (concat v (repeat padding)))))

(def missing-pred
  :nextjournal/missing)

(defn normalize-seq-of-seq [s]
  (let [max-count (count (apply max-key count s))]
    {:rows (mapv #(rpad-vec (value %) max-count missing-pred) s)}))

(defn normalize-seq-of-map [s]
  (let [ks (->> s (mapcat keys) distinct vec)]
    {:head ks
     :rows (mapv (fn [m] (mapv #(get m % missing-pred) ks)) s)}))


(defn normalize-map-of-seq [m]
  (let [ks (-> m keys vec)
        m* (if (seq? (get m (first ks)))
             (reduce (fn [acc [k s]] (assoc acc k (vec s))) {} m)
             m)]
    {:head ks
     :rows (->> (range (count (val (apply max-key (comp count val) m*))))
                (mapv (fn [i] (mapv #(get-in m* [% i] missing-pred) ks))))}))

(defn normalize-seq-to-vec [{:keys [head rows]}]
  (cond-> {:rows (vec rows)}
    head (assoc :head (vec head))))

(defn use-headers [s]
  (let [{:as table :keys [rows]} (normalize-seq-of-seq s)]
    (-> table
        (assoc :head (first rows))
        (update :rows rest))))


(defn normalize-table-data [data]
  (cond
    (and (map? data) (-> data :rows sequential?)) (normalize-seq-to-vec data)
    (and (map? data) (sequential? (first (vals data)))) (normalize-map-of-seq data)
    (and (sequential? data) (map? (first data))) (normalize-seq-of-map data)
    (and (sequential? data) (sequential? (first data))) (normalize-seq-of-seq data)
    :else nil))

(def elide-string-length 100)

(declare with-viewer*)

(defn fetch-all [_ x] x)

(declare !viewers)

;; keep viewer selection stricly in Clojure
(def default-viewers
  ;; maybe make this a sorted-map
  [{:pred char? :render-fn '(fn [c] (v/html [:span.syntax-string.inspected-value "\\" c]))}
   {:pred string? :render-fn (quote v/quoted-string-viewer) :fetch-opts {:n elide-string-length}}
   {:pred number? :render-fn '(fn [x] (v/html [:span.syntax-number.inspected-value
                                               (if (js/Number.isNaN x) "NaN" (str x))]))}
   {:pred symbol? :render-fn '(fn [x] (v/html [:span.syntax-symbol.inspected-value x]))}
   {:pred keyword? :render-fn '(fn [x] (v/html [:span.syntax-keyword.inspected-value (str x)]))}
   {:pred nil? :render-fn '(fn [_] (v/html [:span.syntax-nil.inspected-value "nil"]))}
   {:pred boolean? :render-fn '(fn [x] (v/html [:span.syntax-bool.inspected-value (str x)]))}
   {:pred fn? :name :fn :render-fn '(fn [x] (v/html [:span.inspected-value [:span.syntax-tag "#function"] "[" x "]"]))}
   {:pred map-entry? :name :map-entry :render-fn '(fn [xs opts] (v/html (into [:<>] (comp (v/inspect-children opts) (interpose " ")) xs))) :fetch-opts {:n 2}}
   {:pred vector? :render-fn '(partial v/coll-viewer {:open "[" :close "]"}) :fetch-opts {:n 20}}
   {:pred set? :render-fn '(partial v/coll-viewer {:open "#{" :close "}"}) :fetch-opts {:n 20}}
   {:pred sequential? :render-fn '(partial v/coll-viewer {:open "(" :close ")"}) :fetch-opts {:n 20}}
   {:pred map? :name :map :render-fn 'v/map-viewer :fetch-opts {:n 10}}
   {:pred uuid? :render-fn '(fn [x] (v/html (v/tagged-value "#uuid" [:span.syntax-string.inspected-value "\"" (str x) "\""])))}
   {:pred inst? :render-fn '(fn [x] (v/html (v/tagged-value "#inst" [:span.syntax-string.inspected-value "\"" (str x) "\""])))}
   {:pred var? :transform-fn symbol :render-fn '(fn [x] (v/html [:span.inspected-value [:span.syntax-tag "#'" (str x)]]))}
   {:pred (fn [_] true) :transform-fn pr-str :render-fn '(fn [x] (v/html [:span.inspected-value.whitespace-nowrap.text-gray-700 x]))}
   {:name :elision :render-fn (quote v/elision-viewer)}
   {:name :latex :render-fn (quote v/katex-viewer) :fetch-fn fetch-all}
   {:name :mathjax :render-fn (quote v/mathjax-viewer) :fetch-fn fetch-all}
   {:name :html :render-fn (quote v/html) :fetch-fn fetch-all}
   {:name :hiccup :render-fn (quote v/html)} ;; TODO: drop once markdown doesn't use it anymore
   {:name :plotly :render-fn (quote v/plotly-viewer) :fetch-fn fetch-all}
   {:name :vega-lite :render-fn (quote v/vega-lite-viewer) :fetch-fn fetch-all}
   {:name :markdown :render-fn (quote v/markdown-viewer) :fetch-fn fetch-all}
   {:name :code :render-fn (quote v/code-viewer) :fetch-fn fetch-all}
   {:name :code-folded :render-fn (quote v/foldable-code-viewer) :fetch-fn fetch-all}
   {:name :reagent :render-fn (quote v/reagent-viewer)  :fetch-fn fetch-all}
   {:name :eval! :render-fn (constantly 'nextjournal.clerk.viewer/set-viewers!)}
   {:name :table :render-fn (quote v/table-viewer) :fetch-opts {:n 5}
    :fetch-fn (fn [{:as opts :keys [describe-fn offset]} xs]
                (-> (cond-> (update xs :rows describe-fn opts [])
                      (pos? offset) :rows)
                    (assoc :path [:rows] :replace-path [offset])
                    (dissoc :nextjournal/viewers)))}
   {:name :table-error :render-fn (quote v/table-error) :fetch-opts {:n 1}}
   {:name :object :render-fn '(fn [x] (v/html (v/tagged-value "#object" [v/inspect x])))}
   {:name :file :render-fn '(fn [x] (v/html (v/tagged-value "#file " [v/inspect x])))}
   {:name :clerk/notebook :render-fn (quote v/notebook-viewer) :fetch-fn fetch-all}
   {:name :clerk/result :render-fn (quote v/result-viewer) :fetch-fn fetch-all}])

(def default-table-cell-viewers
  [{:name :elision :render-fn '(fn [_] (v/html "…"))}
   {:pred #{:nextjournal/missing} :render-fn '(fn [x] (v/html [:<>]))}
   {:pred string? :render-fn (quote v/string-viewer) :fetch-opts {:n 100}}
   {:pred number? :render-fn '(fn [x] (v/html [:span.tabular-nums (if (js/Number.isNaN x) "NaN" (str x))]))}])

(defn process-fns [viewers]
  (into []
        (map (fn [{:as viewer :keys [pred fetch-fn render-fn transform-fn]}]
               ;; TODO: simplify with own type for things that should not be transmitted to the
               ;; browser (`pred`, `fetch-fn` & `transform-fn`)
               ;; also remove `symbol?` checks and let viewers use `(quote my-sym)` instead of `'my-sym`
               (cond-> viewer
                 (and pred (or (symbol? pred) (not (ifn? pred))))
                 (update :pred #?(:cljs *eval* :clj #(->Fn+Form '(constantly false) (eval %))))

                 (and transform-fn (or (symbol? transform-fn) (not (ifn? transform-fn))))
                 (update :transform-fn #?(:cljs *eval* :clj #(->Fn+Form '(constantly false) (eval %))))

                 (and fetch-fn (not (ifn? fetch-fn)))
                 (update :fetch-fn #?(:cljs *eval* :clj #(->Fn+Form '(constantly false) (eval %))))

                 #?@(:clj [(and render-fn (not (instance? Form render-fn)))
                           (update :render-fn ->Form)]
                     :cljs [(and render-fn (not (instance? Fn+Form render-fn)))
                            (update :render-fn form->fn+form)]))))
        viewers))

(defn process-viewers [viewers]
  #?(:clj (process-fns viewers)
     :cljs viewers))

(defn processed-default-viewers []
  {:root (process-viewers default-viewers)
   :table (process-viewers default-table-cell-viewers)})

(defonce
  ^{:doc "atom containing a map of `:root` and per-namespace viewers."}
  !viewers
  (#?(:clj atom :cljs ratom/atom) (processed-default-viewers)))

#_(reset! !viewers (processed-default-viewers))

;; heavily inspired by code from Thomas Heller in shadow-cljs, see
;; https://github.com/thheller/shadow-cljs/blob/1708acb21bcdae244b50293d17633ce35a78a467/src/main/shadow/remote/runtime/obj_support.cljc#L118-L144

(defn rank-val [val]
  (reduce-kv (fn [res idx pred]
               (if (and (ifn? pred) (pred val)) (reduced idx) res))
             -1
             (into [] (map :pred) default-viewers)))

(defn resilient-compare [a b]
  (try
    (compare a b)
    (catch #?(:clj Exception :cljs js/Error) _e
      (compare (rank-val a) (rank-val b)))))

(defn ensure-sorted [xs]
  (cond
    (sorted? xs) xs
    (map? xs) (sort-by first resilient-compare xs)
    (set? xs) (sort resilient-compare xs)
    :else xs))

(declare with-viewer*)

(defn find-named-viewer [viewers viewer-name]
  (first (filter (comp #{viewer-name} :name) viewers)))

(defn wrapped-with-viewer
  ([x] (wrapped-with-viewer x default-viewers))
  ([x viewers]
   (if-let [selected-viewer (viewer x)]
     (cond (keyword? selected-viewer)
           (if-let [named-viewer (find-named-viewer viewers selected-viewer)]
             (wrap-value x named-viewer)
             (throw (ex-info (str "cannot find viewer named " selected-viewer) {:selected-viewer selected-viewer :x (value x) :viewers viewers})))
           (instance? Form selected-viewer)
           (wrap-value x selected-viewer))
     (let [val (value x)]
       (loop [v viewers]
         (if-let [{:as matching-viewer :keys [pred]} (first v)]
           (if (and (ifn? pred) (pred val))
             (let [{:keys [render-fn transform-fn]} matching-viewer
                   val (cond-> val transform-fn transform-fn)]
               (if (and transform-fn (not render-fn))
                 (wrapped-with-viewer val viewers)
                 (wrap-value val matching-viewer)))
             (recur (rest v)))
           (throw (ex-info (str "cannot find matchting viewer for `" (pr-str x) "`") {:viewers viewers :x val}))))))))

#_(wrapped-with-viewer {:one :two})
#_(wrapped-with-viewer [1 2 3])
#_(wrapped-with-viewer (range 3))
#_(wrapped-with-viewer (clojure.java.io/file "notebooks"))
#_(wrapped-with-viewer (md "# Hello"))
#_(wrapped-with-viewer (html [:h1 "hi"]))
#_(wrapped-with-viewer (with-viewer* :elision {:remaining 10 :count 30 :offset 19}))
#_(wrapped-with-viewer (with-viewer* (->Form '(fn [name] (html [:<> "Hello " name]))) "James"))


(defn get-viewers
  "Returns all the viewers that apply in precendence of: optional local `viewers`, viewers set per `ns`, as well on the `:root`."
  ([ns] (get-viewers ns nil))
  ([ns expr-viewers]
   (vec (concat expr-viewers (@!viewers ns) (@!viewers :root)))))

(defn closing-paren [{:as _viewer :keys [render-fn name]}]
  (or (when (list? (:form render-fn)) (-> render-fn :form last :close))
      (when (= name :map) "}")))

(defn bounded-count-opts [n xs]
  (assert (number? n) "n must be a number?")
  (let [limit (+ n #?(:clj config/*bounded-count-limit* :cljs 10000))
        count (try (bounded-count limit xs)
                   (catch #?(:clj Exception :cljs js/Error) _
                     nil))]
    (cond-> {}
      count (assoc :count count)
      (or (not count) (= count limit)) (assoc :unbounded? true))))

#_(bounded-count-opts 20 (range))
#_(bounded-count-opts 20 (range 123456))

(defn drop+take-xf
  "Takes a map with optional `:n` and `:offset` and returns a transducer that drops `:offset` and takes `:n`."
  [{:keys [n offset]
    :or {offset 0}}]
  (cond-> (drop offset)
    (pos-int? n)
    (comp (take n))))

#_(sequence (drop+take-xf {:n 10}) (range 100))
#_(sequence (drop+take-xf {:n 10 :offset 10}) (range 100))
#_(sequence (drop+take-xf {}) (range 9))
(declare with-viewer* assign-closing-parens)

(defn describe
  "Returns a subset of a given `value`."
  ([xs]
   (describe xs {}))
  ([xs opts]
   (assign-closing-parens
    (describe xs (merge {:path [] :viewers (process-fns (get-viewers *ns* (viewers xs)))} opts) [])))
  ([xs opts current-path]
   (let [{:as opts :keys [viewers path offset]} (merge {:offset 0} opts)
         wrapped-value (try (wrapped-with-viewer xs viewers) ;; TODO: respect `viewers` on `xs`
                            (catch #?(:clj Exception :cljs js/Error) _ex
                              nil))
         {:as viewer :keys [fetch-opts fetch-fn]} (viewer wrapped-value)
         fetch-opts (merge fetch-opts (select-keys opts [:offset]))
         xs (value wrapped-value)]
     #_(prn :xs xs :type (type xs) :path path :current-path current-path)
     (merge {:path path}
            (dissoc wrapped-value [:nextjournal/value :nextjournal/viewer])
            (with-viewer* (cond-> viewer (map? viewer) (dissoc viewer :pred :transform-fn))
              (cond (< (count current-path)
                       (count path))
                    (let [idx (first (drop (count current-path) path))]
                      (describe (cond (or (map? xs) (set? xs)) (nth (seq (ensure-sorted xs)) idx)
                                      (associative? xs) (get xs idx)
                                      (sequential? xs) (nth xs idx))
                                opts
                                (conj current-path idx)))

                    fetch-fn (fetch-fn (merge opts fetch-opts {:describe-fn describe}) xs)

                    (string? xs)
                    (-> (if (and (number? (:n fetch-opts)) (< (:n fetch-opts) (count xs)))
                          (let [offset (opts :offset 0)
                                total (count xs)
                                new-offset (min (+ offset (:n fetch-opts)) total)
                                remaining (- total new-offset)]
                            (cond-> [(subs xs offset new-offset)]
                              (pos? remaining) (conj (with-viewer* :elision {:path path :count total :offset new-offset :remaining remaining}))
                              true wrap-value
                              true (assoc :replace-path (conj path offset))))
                          xs))

                    (and xs (seqable? xs))
                    (let [count-opts  (if (counted? xs)
                                        {:count (count xs)}
                                        (bounded-count-opts (:n fetch-opts) xs))
                          children (into []
                                         (comp (if (number? (:n fetch-opts)) (drop+take-xf fetch-opts) identity)
                                               (map-indexed (fn [i x] (describe x (-> opts
                                                                                      (dissoc :offset)
                                                                                      (update :path conj (+ i offset))) (conj current-path i))))
                                               (remove nil?))
                                         (ensure-sorted xs))
                          {:keys [count]} count-opts
                          offset (or (-> children peek :path peek) 0)]
                      (cond-> children
                        (or (not count) (< (inc offset) count))

                        (conj (with-viewer* :elision
                                (cond-> (assoc count-opts :offset (inc offset) :path path)
                                  count (assoc :remaining (- count (inc offset))))))))

                    :else ;; leaf value
                    xs))))))


#_#_#_#_
(let [rand-int-seq (fn [n to]
                     (take n (repeatedly #(rand-int to))))
      n 5
      t {:species (repeat n "Adelie")
         :island (repeat n "Biscoe")
         :culmen-length-mm (rand-int-seq n 50)
         :culmen-depth-mm (rand-int-seq n 30)
         :flipper-length-mm (rand-int-seq n 200)
         :body-mass-g (rand-int-seq n 5000)
         :sex (take n (repeatedly #(rand-nth [:female :male])))}
      data t]
  (update (normalize-table-data t) :rows describe))


(let [n (normalize-table-data (repeat 60 ["Adelie" "Biscoe" 50 30 200 5000 :female]))]
  (update n :rows describe))

(def t' (with-viewer* :table (repeat 60 ["Adelie" "Biscoe" 50 30 200 5000 :female])))

(let [xs t']
  (describe xs))
#_
(describe (table
            {:a (range 30)
             :b (map inc (range 30))}))

(comment
  (describe 123)
  (-> (describe (range 100)) value peek)
  (describe {:hello [1 2 3]})
  (describe {:one [1 2 3] 1 2 3 4})
  (describe [1 2 [1 [2] 3] 4 5])
  (describe (clojure.java.io/file "notebooks"))
  (describe {:viewers [{:pred sequential? :render-fn pr-str}]} (range 100))
  (describe (map vector (range)))
  (describe (subs (slurp "/usr/share/dict/words") 0 1000))
  (describe (plotly {:data [{:z [[1 2 3] [3 2 1]] :type "surface"}]}))
  (describe (with-viewer* :html [:h1 "hi"]))
  (describe (with-viewer* :html [:ul (for [x (range 3)] [:li x])]))
  (describe (range))
  (describe {1 [2]})
  (describe (with-viewer* (->Form '(fn [name] (html [:<> "Hello " name]))) "James")))


(defn desc->values
  "Takes a `description` and returns its value. Inverse of `describe`. Mostly useful for debugging."
  [desc]
  (let [x (value desc)
        viewer (viewer desc)]
    (if (= viewer :elision)
      '…
      (cond->> x
        (vector? x)
        (into (case (:name viewer) (:map :table) {} [])
              (map desc->values))))))

#_(desc->values (describe [1 [2 {:a :b} 2] 3 (range 100)]))
#_(desc->values (describe (with-viewer* :table (normalize-table-data (repeat 60 ["Adelie" "Biscoe" 50 30 200 5000 :female])))))

(defn path-to-value [path]
  (conj (interleave path (repeat :nextjournal/value)) :nextjournal/value))


(defn merge-descriptions [root more]
  (update-in root (path-to-value (:path more))
             (fn [value]
               (let [{:keys [offset path]} (-> value peek :nextjournal/value)
                     path-from-value (conj path offset)
                     path-from-more (or (:replace-path more) ;; string case, TODO find a better way to unify
                                        (-> more :nextjournal/value first :path))]
                 (when (not= path-from-value path-from-more)
                     (throw (ex-info "paths mismatch" {:path-from-value path-from-value :path-from-more path-from-more})))
                   (into (pop value) (:nextjournal/value more))))))

  (comment ;; test cases for merge-descriptions, TODO: simplify
    (let [value (range 30)
          desc (describe value)
          path []
          elision (peek (get-in desc (path-to-value path)))
          more (describe value (:nextjournal/value elision))]

      (merge-descriptions desc more))

  (let [value [(range 30)]
        desc (describe value)
        path [0]
        elision (peek (get-in desc (path-to-value path)))
        more (describe value (:nextjournal/value elision))]
    (merge-descriptions desc more))

  (let [value (str/join (map #(str/join (repeat 70 %)) ["a" "b" #_#_ "c" "d"]))
        desc (describe value)
        path []
        elision (peek (get-in desc (path-to-value path)))
        more (describe value (:nextjournal/value elision))]
    (merge-descriptions desc more)))

(defn assign-closing-parens
  ([node] (assign-closing-parens {} node))
  ([{:as ctx :keys [closing-parens]} node]
   (let [value (value node)
         viewer (viewer node)
         closing (closing-paren viewer)
         non-leaf? (and (vector? value) (wrapped-value? (first value)))
         defer-closing? (and non-leaf?
                             (or (-> value last :nextjournal/viewer closing-paren) ;; the last element can carry parens
                                 (and (= :map-entry (-> value last :nextjournal/viewer :name)) ;; the last element is a map entry whose value can carry parens
                                      (-> value last :nextjournal/value last :nextjournal/viewer closing-paren))))]
     (cond-> node
       closing (assoc-in [:nextjournal/viewer :closing-parens] (when-not defer-closing? (cons closing closing-parens)))
       non-leaf? (update :nextjournal/value
                         (fn [xs]
                           (into []
                                 (map-indexed (fn [i x]
                                                (assign-closing-parens (if (and defer-closing? (= (dec (count xs)) i))
                                                                         (update ctx :closing-parens #(cond->> % closing (cons closing)))
                                                                         (assoc ctx :closing-parens nil))
                                                                       x)))
                                 xs)))))))


#?(:clj
   (defn datafy-scope [scope]
     (cond
       (instance? clojure.lang.Namespace scope) {:namespace (-> scope str keyword)}
       (keyword? scope) scope
       :else (throw (ex-info (str "Unsupported scope " scope) {:scope scope})))))

#_(datafy-scope *ns*)
#_(datafy-scope #'datafy-scope)

(declare with-viewer*)

#?(:clj
   (defn set-viewers!* [scope viewers]
     (assert (or (#{:root} scope)
                 (instance? clojure.lang.Namespace scope)
                 (var? scope)))
     (let [viewers (process-fns viewers)]
       (swap! !viewers assoc scope viewers)
       (with-viewer* :eval! `'(v/set-viewers! ~(datafy-scope scope) ~viewers)))))


(defn registration? [x]
  (and (map? x) (contains? #{:eval!} (viewer x))))

#_(registration? (set-viewers! []))
#_(nextjournal.clerk/show! "notebooks/viewers/vega.clj")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; public api

(defn with-viewer*
  "Wraps given "
  [viewer x]
  (-> x
      wrap-value
      (assoc :nextjournal/viewer viewer)))

#_(with-viewer- :latex "x^2")

(defn with-viewers*
  "Binds viewers to types, eg {:boolean view-fn}"
  [viewers x]
  (-> x
      wrap-value
      (assoc :nextjournal/viewers viewers)))

#_(->> "x^2" (with-viewer* :latex) (with-viewers* [{:name :latex :render-fn :mathjax}]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; public convience api
(def html      (partial with-viewer* :html))
(def md        (partial with-viewer* :markdown))
(def plotly    (partial with-viewer* :plotly))
(def vl        (partial with-viewer* :vega-lite))
(def tex       (partial with-viewer* :latex))
(def notebook  (partial with-viewer* :clerk/notebook))

(defn table
  "Displays `xs` in a table.

  Performs normalization from the following formats:

  * maps of seqs: `{:column-1 [1 2] :column-2 [3 4]}`
  * seq of maps: `[{:column-1 1 :column-2 3} {:column-1 2 :column-2 4}]`
  * seq of seqs `[[1 3] [2 4]]`
  * map with `:head` and `:rows` keys `{:head [:column-1 :column-2] :rows [[1 3] [2 4]]}`"
  ([xs]
   (table {:nextjournal/width :wide} xs))
  ([opts xs]
   (-> (if-let [normalized (normalize-table-data xs)]
         (with-viewer* :table normalized)
         (with-viewer* :table-error [xs]))
       (merge opts)
       (update :nextjournal/viewers concat (:table @!viewers)))))

#_(table {:a (range 10)
          :b (mapv inc (range 10))})
#_(describe (table (set (range 10))))

(defn code [x]
  (with-viewer* :code (if (string? x) x (with-out-str (pprint/pprint x)))))

#_(code '(+ 1 2))

(defn exception [e]
  (let [{:keys [via trace]} e]
    (html
     [:div.w-screen.h-screen.overflow-y-auto.bg-gray-100.p-6.text-xs.monospace.flex.flex-col
      [:div.rounded-md.shadow-lg.border.border-gray-300.bg-white.max-w-6xl.mx-auto
       (into
        [:div]
        (map
         (fn [{:as ex :keys [type message data trace]}]
           [:div.p-4.bg-red-100.border-b.border-gray-300.rounded-t-md
            [:div.font-bold "Unhandled " type]
            [:div.font-bold.mt-1 message]
            [:div.mt-1 (pr-str data)]])
         via))
       [:div.py-6
        [:table.w-full
         (into [:tbody]
               (map (fn [[call x file line]]
                      [:tr.hover:bg-red-100.leading-tight
                       [:td.text-right.px-6 file ":"]
                       [:td.text-right.pr-6 line]
                       [:td.py-1.pr-6 #?(:clj (demunge (pr-str call)) :cljs call)]]))
               trace)]]]])))

#_(nextjournal.clerk/show! "notebooks/boom.clj")
