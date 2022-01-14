(ns nextjournal.clerk.viewer
  (:require [clojure.string :as str]
            [clojure.pprint :as pprint]
            [clojure.datafy :as datafy]
            #?@(:clj [[clojure.repl :refer [demunge]]
                      [nextjournal.clerk.config :as config]]
                :cljs [[reagent.ratom :as ratom]]))
  #?(:clj (:import (java.lang Throwable)
                   (java.awt.image BufferedImage)
                   (javax.imageio ImageIO))))

(defrecord ViewerEval [form])
(defrecord ViewerFn [form #?(:cljs f)]
  #?@(:cljs [IFn
             (-invoke [this x] ((:f this) x))
             (-invoke [this x y] ((:f this) x y))]))


(defn viewer-fn? [x]
  (instance? ViewerFn x))

(defn ->viewer-fn [form]
  (map->ViewerFn {:form form :f #?(:clj nil :cljs (*eval* form))}))

(defn ->viewer-eval [form]
  (map->ViewerEval {:form form}))

#?(:clj
   (defmethod print-method ViewerFn [v ^java.io.Writer w]
     (.write w (str "#viewer-fn " (pr-str `~(:form v))))))

#?(:clj
   (defmethod print-method ViewerEval [v ^java.io.Writer w]
     (.write w (str "#viewer-eval " (pr-str `~(:form v))))))

#_(binding [*data-readers* {'viewer-fn ->viewer-fn}]
    (read-string (pr-str (->viewer-fn '(fn [x] x)))))
#_(binding [*data-readers* {'viewer-fn ->viewer-fn}]
    (read-string (pr-str (->viewer-fn 'number?))))

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

(defn demunge-ex-data [ex-data]
  (update ex-data :trace (fn [traces] (mapv #(update % 0 (comp demunge pr-str)) traces))))

#_(demunge-ex-data (datafy/datafy (ex-info "foo" {:bar :baz})))

(def elide-string-length 100)

(defn fetch-all [_ x] x)

(defn- var-from-def? [x]
  (get x :nextjournal.clerk/var-from-def))

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
   {:pred var-from-def? :transform-fn (fn [x] (-> x :nextjournal.clerk/var-from-def deref))}
   {:pred vector? :render-fn 'v/coll-viewer :opening-paren "[" :closing-paren "]" :fetch-opts {:n 20}}
   {:pred set? :render-fn 'v/coll-viewer :opening-paren "#{" :closing-paren "}" :fetch-opts {:n 20}}
   {:pred sequential? :render-fn 'v/coll-viewer :opening-paren "(" :closing-paren ")" :fetch-opts {:n 20}}
   {:pred map? :name :map :render-fn 'v/map-viewer :opening-paren "{" :closing-paren "}" :fetch-opts {:n 10}}
   {:pred uuid? :render-fn '(fn [x] (v/html (v/tagged-value "#uuid" [:span.syntax-string.inspected-value "\"" (str x) "\""])))}
   {:pred inst? :render-fn '(fn [x] (v/html (v/tagged-value "#inst" [:span.syntax-string.inspected-value "\"" (str x) "\""])))}
   {:pred var? :transform-fn symbol :render-fn '(fn [x] (v/html [:span.inspected-value [:span.syntax-tag "#'" (str x)]]))}
   {:pred (fn [e] (instance? #?(:clj Throwable :cljs js/Error) e)) :fetch-fn fetch-all
    :name :error :render-fn (quote v/throwable-viewer) :transform-fn (comp demunge-ex-data datafy/datafy)}
   #?(:clj {:pred #(instance? BufferedImage %)
            :fetch-fn (fn [_ image] (let [stream (java.io.ByteArrayOutputStream.)
                                          w (.getWidth image)
                                          h (.getHeight image)
                                          r (float (/ w h))]
                                      (ImageIO/write image "png" stream)
                                      (cond-> {:nextjournal/value (.toByteArray stream)
                                               :nextjournal/content-type "image/png"}
                                        (< 2 r) (assoc :nextjournal/width :full))))
            :render-fn '(fn [blob] (v/html [:img {:src (v/url-for blob)}]))})
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
                ;; TODO: use budget per row for table
                ;; TODO: opt out of eliding cols
                (-> (cond-> (update xs :rows describe-fn (dissoc opts :!budget) [])
                      (pos? offset) :rows)
                    (assoc :path [:rows] :replace-path [offset])
                    (dissoc :nextjournal/viewers)))}
   {:name :table-error :render-fn (quote v/table-error) :fetch-opts {:n 1}}
   {:name :object :render-fn '(fn [x] (v/html (v/tagged-value "#object" [v/inspect x])))}
   {:name :file :render-fn '(fn [x] (v/html (v/tagged-value "#file " [v/inspect x])))}
   {:name :clerk/notebook :render-fn (quote v/notebook-viewer) :fetch-fn fetch-all}
   {:name :clerk/result :render-fn (quote v/result-viewer) :fetch-fn fetch-all}
   {:name :hide-result :transform-fn (fn [_] nil)}])

(def default-table-cell-viewers
  [{:name :elision :render-fn '(fn [_] (v/html "…"))}
   {:pred #{:nextjournal/missing} :render-fn '(fn [x] (v/html [:<>]))}
   {:pred string? :render-fn (quote v/string-viewer) :fetch-opts {:n 100}}
   {:pred number? :render-fn '(fn [x] (v/html [:span.tabular-nums (if (js/Number.isNaN x) "NaN" (str x))]))}])

(def all-viewers
  {:root  default-viewers
   :table default-table-cell-viewers})

(defonce
  ^{:doc "atom containing a map of `:root` and per-namespace viewers."}
  !viewers
  (#?(:clj atom :cljs ratom/atom) all-viewers))

#_(reset! !viewers all-viewers)

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

(declare with-viewer)

(defn find-named-viewer [viewers viewer-name]
  (first (filter (comp #{viewer-name} :name) viewers)))

(defn wrapped-with-viewer
  ([x] (wrapped-with-viewer x default-viewers))
  ([x viewers]
   (if-let [selected-viewer (viewer x)]
     (cond (keyword? selected-viewer)
           (if-let [{:as named-viewer :keys [transform-fn]} (find-named-viewer viewers selected-viewer)]
             (wrap-value (cond-> x transform-fn transform-fn) named-viewer)
             (throw (ex-info (str "cannot find viewer named " selected-viewer) {:selected-viewer selected-viewer :x (value x) :viewers viewers})))
           (viewer-fn? selected-viewer)
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
#_(wrapped-with-viewer (with-viewer :elision {:remaining 10 :count 30 :offset 19}))
#_(wrapped-with-viewer (with-viewer (->Form '(fn [name] (html [:<> "Hello " name]))) "James"))

(defn get-viewers
  "Returns all the viewers that apply in precendence of: optional local `viewers`, viewers set per `ns`, as well on the `:root`."
  ([ns] (get-viewers ns nil))
  ([ns expr-viewers]
   (vec (concat expr-viewers (@!viewers ns) (@!viewers :root)))))

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
#_(bounded-count-opts 20 (range 3234567))

(defn drop+take-xf
  "Takes a map with optional `:n` and `:offset` and returns a transducer that drops `:offset` and takes `:n`."
  [{:keys [n offset]
    :or {offset 0}}]
  (cond-> (drop offset)
    (int? n)
    (comp (take n))))

#_(sequence (drop+take-xf {:n 10}) (range 100))
#_(sequence (drop+take-xf {:n 10 :offset 10}) (range 100))
#_(sequence (drop+take-xf {}) (range 9))


(declare with-viewer assign-closing-parens)

(defn process-render-fn [{:as viewer :keys [render-fn]}]
  (cond-> viewer
    (and render-fn (not (viewer-fn? render-fn)))
    (update :render-fn ->viewer-fn)))

(defn process-viewer [viewer]
  (if-not (map? viewer)
    viewer
    (-> viewer
        (dissoc :pred :transform-fn :fetch-fn)
        process-render-fn)))

#_(process-viewer {:render-fn '(v/html [:h1]) :fetch-fn fetch-all})

(defn describe
  "Returns a subset of a given `value`."
  ([xs]
   (describe xs {}))
  ([xs opts]
   (assign-closing-parens
    (describe xs (merge {:!budget (atom (:budget opts 200)) :path [] :viewers (get-viewers *ns* (viewers xs))} opts) [])))
  ([xs opts current-path]
   (let [{:as opts :keys [!budget viewers path offset]} (merge {:offset 0} opts)
         wrapped-value (try (wrapped-with-viewer xs viewers) ;; TODO: respect `viewers` on `xs`
                            (catch #?(:clj Exception :cljs js/Error) _ex
                              (do (println _ex)
                                  nil)))
         {:as viewer :keys [fetch-opts fetch-fn]} (viewer wrapped-value)
         fetch-opts (merge fetch-opts (select-keys opts [:offset]))
         descend? (< (count current-path)
                     (count path))
         xs (value wrapped-value)]
     #_(prn :xs xs :type (type xs) :path path :current-path current-path)
     (when (and !budget (not descend?) (not fetch-fn))
       (swap! !budget #(max (dec %) 0)))
     (merge {:path path}
            (dissoc wrapped-value [:nextjournal/value :nextjournal/viewer])
            (with-viewer (process-viewer viewer)
              (cond descend?
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
                              (pos? remaining) (conj (with-viewer :elision {:path path :count total :offset new-offset :remaining remaining}))
                              true wrap-value
                              true (assoc :replace-path (conj path offset))))
                          xs))

                    (and xs (seqable? xs))
                    (let [count-opts  (if (counted? xs)
                                        {:count (count xs)}
                                        (bounded-count-opts (:n fetch-opts) xs))
                          fetch-opts (cond-> fetch-opts
                                       (and (:n fetch-opts) !budget (not (map-entry? xs)))
                                       (update :n min @!budget))
                          children (into []
                                         (comp (if (number? (:n fetch-opts)) (drop+take-xf fetch-opts) identity)
                                               (map-indexed (fn [i x] (describe x (-> opts
                                                                                      (dissoc :offset)
                                                                                      (update :path conj (+ i offset))) (conj current-path i))))
                                               (remove nil?))
                                         (ensure-sorted xs))
                          {:keys [count]} count-opts
                          offset (or (-> children peek :path peek) -1)]
                      (cond-> children
                        (or (not count) (< (inc offset) count))

                        (conj (with-viewer :elision
                                (cond-> (assoc count-opts :offset (inc offset) :path path)
                                  count (assoc :remaining (- count (inc offset))))))))

                    :else ;; leaf value
                    xs))))))

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
  (describe (with-viewer :html [:h1 "hi"]))
  (describe (with-viewer :html [:ul (for [x (range 3)] [:li x])]))
  (describe (range))
  (describe {1 [2]})
  (describe (with-viewer (->Form '(fn [name] (html [:<> "Hello " name]))) "James")))

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
#_(desc->values (describe (with-viewer :table (normalize-table-data (repeat 60 ["Adelie" "Biscoe" 50 30 200 5000 :female])))))

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


(defn assign-closing-parens
  ([node] (assign-closing-parens '() node))
  ([closing-parens node]
   (let [value (value node)
         viewer (viewer node)
         closing (:closing-paren viewer)
         non-leaf? (and (vector? value) (wrapped-value? (first value)))
         defer-closing? (and non-leaf?
                             (or (-> value last :nextjournal/viewer :closing-paren) ;; the last element can carry parens
                                 (and (= :map-entry (-> value last :nextjournal/viewer :name)) ;; the last element is a map entry whose value can carry parens
                                      (-> value last :nextjournal/value last :nextjournal/viewer :closing-paren))))]
     (cond-> (cond
               (not closing) node
               defer-closing? (update node :nextjournal/viewer dissoc :closing-paren)
               :else (update-in node [:nextjournal/viewer :closing-paren] cons closing-parens))
       non-leaf? (update :nextjournal/value
                         (fn [xs]
                           (into []
                                 (map-indexed (fn [i x]
                                                (assign-closing-parens (if (and defer-closing? (= (dec (count xs)) i))
                                                                         (cond->> closing-parens closing (cons closing))
                                                                         '())
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

(declare with-viewer)

#?(:clj
   (defn set-viewers!
     ([viewers] (set-viewers! *ns* viewers))
     ([scope viewers]
      (assert (or (#{:root} scope)
                  (instance? clojure.lang.Namespace scope)
                  (var? scope)))
      (swap! !viewers assoc scope viewers)
      (with-viewer :eval! `'(v/set-viewers! ~(datafy-scope scope) ~viewers)))))


(defn registration? [x]
  (and (map? x) (contains? #{:eval!} (viewer x))))

#_(registration? (set-viewers! []))
#_(nextjournal.clerk/show! "notebooks/viewers/vega.clj")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; public api

(defn with-viewer
  "Wraps given "
  [viewer x]
  (-> x
      wrap-value
      (assoc :nextjournal/viewer (cond-> viewer
                                   (or (list? viewer) (symbol? viewer))
                                   ->viewer-fn))))

#_(with-viewer :latex "x^2")
#_(with-viewer '#(v/html [:h3 "Hello " % "!"]) "x^2")

(defn with-viewers
  "Binds viewers to types, eg {:boolean view-fn}"
  [viewers x]
  (-> x
      wrap-value
      (assoc :nextjournal/viewers viewers)))

#_(->> "x^2" (with-viewer :latex) (with-viewers [{:name :latex :render-fn :mathjax}]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; public convience api
(def html         (partial with-viewer :html))
(def md           (partial with-viewer :markdown))
(def plotly       (partial with-viewer :plotly))
(def vl           (partial with-viewer :vega-lite))
(def tex          (partial with-viewer :latex))
(def hide-result  (partial with-viewer :hide-result))
(def notebook     (partial with-viewer :clerk/notebook))
(defn doc-url [path]
  (->viewer-eval (list 'v/doc-url path)))


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
         (with-viewer :table normalized)
         (with-viewer :table-error [xs]))
       (merge opts)
       (update :nextjournal/viewers concat (:table @!viewers)))))

#_(table {:a (range 10)
          :b (mapv inc (range 10))})
#_(describe (table (set (range 10))))

(defn code [x]
  (with-viewer :code (if (string? x) x (with-out-str (pprint/pprint x)))))

#_(code '(+ 1 2))
