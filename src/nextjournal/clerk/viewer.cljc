(ns nextjournal.clerk.viewer
  (:require [clojure.string :as str]
            [clojure.pprint :as pprint]
            [clojure.datafy :as datafy]
            [clojure.set :as set]
            [clojure.walk :as w]
            #?@(:clj [[clojure.repl :refer [demunge]]
                      [nextjournal.clerk.config :as config]
                      [nextjournal.clerk.hashing :as hashing]]
                :cljs [[reagent.ratom :as ratom]])
            [nextjournal.markdown :as md]
            [nextjournal.markdown.transform :as md.transform]
            [lambdaisland.uri.normalize :as uri.normalize])
  #?(:clj (:import (clojure.lang IDeref)
                   (java.lang Throwable)
                   (java.awt.image BufferedImage)
                   (javax.imageio ImageIO)
                   (java.util Base64))))

(defrecord ViewerEval [form])

(defrecord ViewerFn [form #?(:cljs f)]
  #?@(:cljs [IFn
             (-invoke [this x] ((:f this) x))
             (-invoke [this x y] ((:f this) x y))]))


(defn viewer-fn? [x]
  (instance? ViewerFn x))

(defn ->viewer-fn [form]
  (map->ViewerFn {:form form :f #?(:clj nil :cljs (eval form))}))

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

(defn wrapped-value?
  "Tests if `x` is a map containing a `:nextjournal/value`."
  [x]
  (and (map? x) ;; can throw for `sorted-map`
       (try (contains? x :nextjournal/value)
            (catch #?(:clj Exception :cljs js/Error) _e false))))

;; TODO: think about naming this to indicate it does nothing if the value is already wrapped.
(defn wrap-value
  "Ensures `x` is wrapped in a map under a `:nextjournal/value` key."
  ([x] (if (wrapped-value? x) x {:nextjournal/value x}))
  ([x v] (-> x wrap-value (assoc :nextjournal/viewer v))))

#_(wrap-value 123)
#_(wrap-value {:nextjournal/value 456})

(defn ->value
  "Takes `x` and returns the `:nextjournal/value` from it, or otherwise `x` unmodified."
  [x]
  (if (wrapped-value? x)
    (:nextjournal/value x)
    x))

#_(->value (with-viewer :code '(+ 1 2 3)))
#_(->value 123)

(defn ->viewer
  "Returns the `:nextjournal/viewer` for a given wrapped value `x`, `nil` otherwise."
  [x]
  (when (wrapped-value? x)
    (:nextjournal/viewer x)))


#_(->viewer (with-viewer :code '(+ 1 2 3)))
#_(->viewer "123")

(defn ->viewers
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
    {:rows (mapv #(rpad-vec (->value %) max-count missing-pred) s)}))

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

(declare describe with-viewer wrapped-with-viewer datafy-scope normalize-viewer get-viewers)

(defn inspect-leafs [opts x]
  (if (wrapped-value? x)
    [#?(:clj (->viewer-eval 'v/inspect) :cljs (eval 'v/inspect)) (describe x opts)]
    x))

(defn fetch-all [opts xs]
  (w/postwalk (partial inspect-leafs opts) xs))

(defn get-safe
  ([key] #(get-safe % key))
  ([map key]
   (when (map? map)
     (try (get map key) ;; can throw for e.g. sorted-map
          (catch #?(:clj Exception :cljs js/Error) _e nil)))))

(def var-from-def?
  (get-safe :nextjournal.clerk/var-from-def))

(def datafied?
  (get-safe :nextjournal.clerk/datafied))

(defn with-md-viewer [{:as node :keys [type]}]
  (with-viewer (keyword "nextjournal.markdown" (name type)) node))

(defn into-markup [mkup]
  (let [mkup-fn (if (fn? mkup) mkup (constantly mkup))]
    (fn [{:as node :keys [text content]}]
      (with-viewer :html
        (into (mkup-fn node) (cond text [text] content (map with-md-viewer content)))))))

#?(:clj
   (defn ->edn [x]
     (binding [*print-namespace-maps* false]
       (pr-str x))))

#_(->edn {:nextjournal/value :foo})

#?(:clj
   (defn base64-encode-value [{:as result :nextjournal/keys [content-type]}]
     (update result :nextjournal/value (fn [data] (str "data:" content-type ";base64, "
                                                       (.encodeToString (Base64/getEncoder) data))))))

(defn apply-viewer-unwrapping-var-from-def [{:as result :nextjournal/keys [value viewer]}]
  (if viewer
    (let [{:keys [transform-fn]} (and (map? viewer) viewer)
          value (if (and (not transform-fn) (get value :nextjournal.clerk/var-from-def))
                  (-> value :nextjournal.clerk/var-from-def deref)
                  value)]
      (assoc result :nextjournal/value (if (or (var? viewer) (fn? viewer))
                                         (viewer value)
                                         {:nextjournal/value value
                                          :nextjournal/viewer (normalize-viewer viewer)})))
    result))

#_(apply-viewer-unwrapping-var-from-def {:nextjournal/value [:h1 "hi"] :nextjournal/viewer :html})
#_(apply-viewer-unwrapping-var-from-def {:nextjournal/value [:h1 "hi"] :nextjournal/viewer (resolve 'nextjournal.clerk/html)})

#?(:clj
   (defn extract-blobs [lazy-load? blob-id described-result]
     (w/postwalk #(cond-> %
                    (and (get % :nextjournal/content-type) lazy-load?)
                    (assoc :nextjournal/value {:blob-id blob-id :path (:path %)})
                    (and (get % :nextjournal/content-type) (not lazy-load?))
                    base64-encode-value)
                 described-result)))

#?(:clj
   (defn ->result [ns {:as result :nextjournal/keys [value blob-id] vs :nextjournal/viewers} lazy-load?]
     (let [described-result (extract-blobs lazy-load? blob-id (describe value {:viewers (concat vs (get-viewers ns (->viewers value)))}))
           opts-from-form-meta (select-keys result [:nextjournal/width])]
       (merge {:nextjournal/viewer :clerk/result
               :nextjournal/value (cond-> (try {:nextjournal/edn (->edn described-result)}
                                               (catch Throwable _e
                                                 {:nextjournal/string (pr-str value)}))
                                    (-> described-result ->viewer :name)
                                    (assoc :nextjournal/viewer (select-keys (->viewer described-result) [:name]))

                                    lazy-load?
                                    (assoc :nextjournal/fetch-opts {:blob-id blob-id}
                                           :nextjournal/hash (hashing/->hash-str [blob-id described-result])))}
              (dissoc described-result :nextjournal/value :nextjournal/viewer)
              opts-from-form-meta))))

(defn result-hidden? [result] (= :hide-result (-> result ->value ->viewer)))

(defn ->display [{:as code-cell :keys [result ns?]}]
  (let [{:nextjournal.clerk/keys [visibility]} result
        result? (and (contains? code-cell :result)
                     (not (result-hidden? result))
                     (not (contains? visibility :hide-ns))
                     (not (and ns? (contains? visibility :hide))))
        fold? (and (not (contains? visibility :hide-ns))
                   (or (contains? visibility :fold)
                       (contains? visibility :fold-ns)))
        code? (or fold? (contains? visibility :show))]
    {:result? result? :fold? fold? :code? code?}))

#_(->display {:result {:nextjournal.clerk/visibility #{:fold :hide-ns}}})
#_(->display {:result {:nextjournal.clerk/visibility #{:fold-ns}}})
#_(->display {:result {:nextjournal.clerk/visibility #{:hide}} :ns? false})
#_(->display {:result {:nextjournal.clerk/visibility #{:fold}} :ns? true})
#_(->display {:result {:nextjournal.clerk/visibility #{:fold}} :ns? false})
#_(->display {:result {:nextjournal.clerk/visibility #{:hide} :nextjournal/value {:nextjournal/viewer :hide-result}} :ns? false})
#_(->display {:result {:nextjournal.clerk/visibility #{:hide}} :ns? true})

#?(:clj
   (defn with-block-viewer [{:keys [ns inline-results?]} {:as cell :keys [type]}]
     (case type
       :markdown [(with-viewer :clerk/markdown-block cell)]
       :code (let [{:as cell :keys [result]} (update cell :result apply-viewer-unwrapping-var-from-def)
                   {:as display-opts :keys [code? result?]} (->display cell)]
               (cond-> []
                 code?
                 (conj (with-viewer :clerk/code-block
                         ;; TODO: display analysis could be merged into cell earlier
                         (-> cell (merge display-opts) (dissoc :result))))
                 result?
                 (conj (->result ns result (and (not inline-results?)
                                                (contains? result :nextjournal/blob-id)))))))))

(defn update-viewers [viewers select-fn->update-fn]
  (reduce (fn [viewers [pred update-fn]]
            (mapv (fn [viewer]
                    (cond-> viewer
                      (pred viewer) update-fn)) viewers))
          viewers
          select-fn->update-fn))

#_ (update-viewers default-viewers {:fetch-opts #(dissoc % :fetch-opts)})

(defn prepend [viewers viewers-to-prepend]
  (into (vec viewers-to-prepend) viewers))

(defn update-table-viewers [viewers]
  (-> viewers
      (update-viewers {(comp #{:elision} :name) #(assoc % :render-fn '(fn [_] (v/html "…")))
                       (comp #{string?} :pred) #(assoc % :render-fn (quote v/string-viewer))
                       (comp #{number?} :pred) #(assoc % :render-fn '(fn [x] (v/html [:span.tabular-nums (if (js/Number.isNaN x) "NaN" (str x))])))})
      (prepend [{:pred #{:nextjournal/missing} :render-fn '(fn [x] (v/html [:<>]))}])))

#?(:clj (def utc-date-format ;; from `clojure.instant/thread-local-utc-date-format`
          (doto (java.text.SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ss.SSS-00:00")
            (.setTimeZone (java.util.TimeZone/getTimeZone "GMT")))))

;; keep viewer selection stricly in Clojure
(def default-viewers
  ;; maybe make this a sorted-map
  [{:pred char? :render-fn '(fn [c] (v/html [:span.cmt-string.inspected-value "\\" c]))}
   {:pred string? :render-fn (quote v/quoted-string-viewer) :fetch-opts {:n elide-string-length}}
   {:pred number? :render-fn (quote v/number-viewer)}
   {:name :number-hex :render-fn '(fn [num] (v/number-viewer (str "0x" (.toString (js/Number. num) 16))))}
   {:pred symbol? :render-fn '(fn [x] (v/html [:span.cmt-keyword.inspected-value (str x)]))}
   {:pred keyword? :render-fn '(fn [x] (v/html [:span.cmt-atom.inspected-value (str x)]))}
   {:pred nil? :render-fn '(fn [_] (v/html [:span.cmt-default.inspected-value "nil"]))}
   {:pred boolean? :render-fn '(fn [x] (v/html [:span.cmt-bool.inspected-value (str x)]))}
   {:pred map-entry? :name :map-entry :render-fn '(fn [xs opts] (v/html (into [:<>] (comp (v/inspect-children opts) (interpose " ")) xs))) :fetch-opts {:n 2}}
   {:pred var-from-def? :transform-fn (fn [x] (-> x :nextjournal.clerk/var-from-def deref))}
   {:name :read+inspect :render-fn '(fn [x] (v/html [v/inspect-paginated (try (v/read-string x)
                                                                              (catch js/Error _e
                                                                                (v/with-viewer v/unreadable-edn-viewer x)))]))}
   {:pred vector? :render-fn 'v/coll-viewer :opening-paren "[" :closing-paren "]" :fetch-opts {:n 20}}
   {:pred set? :render-fn 'v/coll-viewer :opening-paren "#{" :closing-paren "}" :fetch-opts {:n 20}}
   {:pred sequential? :render-fn 'v/coll-viewer :opening-paren "(" :closing-paren ")" :fetch-opts {:n 20}}
   {:pred map? :name :map :render-fn 'v/map-viewer :opening-paren "{" :closing-paren "}" :fetch-opts {:n 10}}
   {:pred var? :transform-fn symbol :render-fn '(fn [x] (v/html [:span.inspected-value [:span.cmt-meta "#'" (str x)]]))}
   {:pred (fn [e] (instance? #?(:clj Throwable :cljs js/Error) e)) :fetch-fn fetch-all
    :name :error :render-fn (quote v/throwable-viewer) :transform-fn (comp demunge-ex-data datafy/datafy)}
   #?(:clj {:pred #(instance? BufferedImage %)
            :fetch-fn (fn [_ image] (let [stream (java.io.ByteArrayOutputStream.)
                                          w (.getWidth image)
                                          h (.getHeight image)
                                          r (float (/ w h))]
                                      (ImageIO/write image "png" stream)
                                      (cond-> {:nextjournal/value (.toByteArray stream)
                                               :nextjournal/content-type "image/png"
                                               :nextjournal/width (if (and (< 2 r) (< 900 w)) :full :wide)})))
            :render-fn '(fn [blob] (v/html [:figure.flex.flex-col.items-center.not-prose [:img {:src (v/url-for blob)}]]))})
   {:pred #(instance? IDeref %)
    :transform-fn (fn [r] (with-viewer :tagged-value
                            {:tag "object"
                             :value (vector (type r)
                                            #?(:clj (with-viewer :number-hex (System/identityHashCode r)))
                                            (if-let [deref-as-map (resolve 'clojure.core/deref-as-map)]
                                              (deref-as-map r)
                                              r))}))}
   {:pred (constantly :true) :transform-fn #(with-viewer :read+inspect (pr-str %))}
   {:name :elision :render-fn (quote v/elision-viewer) :fetch-fn fetch-all}
   {:name :latex :render-fn (quote v/katex-viewer) :fetch-fn fetch-all}
   {:name :mathjax :render-fn (quote v/mathjax-viewer) :fetch-fn fetch-all}
   {:name :html :render-fn (quote v/html) :fetch-fn fetch-all}
   {:name :plotly :render-fn (quote v/plotly-viewer) :fetch-fn fetch-all}
   {:name :vega-lite :render-fn (quote v/vega-lite-viewer) :fetch-fn fetch-all}
   {:name :markdown :transform-fn (comp with-md-viewer md/parse)}
   {:name :code :render-fn (quote v/code-viewer) :fetch-fn fetch-all :transform-fn #(let [v (->value %)] (if (string? v) v (str/trim (with-out-str (pprint/pprint v)))))}
   {:name :code-folded :render-fn (quote v/foldable-code-viewer) :fetch-fn fetch-all :transform-fn #(let [v (->value %)] (if (string? v) v (with-out-str (pprint/pprint v))))}
   {:name :reagent :render-fn (quote v/reagent-viewer)  :fetch-fn fetch-all}
   {:name :table :render-fn (quote v/table-viewer) :fetch-opts {:n 5}
    :update-viewers-fn update-table-viewers
    :transform-fn (fn [xs]
                    (-> (wrap-value xs)
                        (update :nextjournal/width #(or % :wide))
                        (update :nextjournal/value #(or (normalize-table-data %)
                                                        {:error "Could not normalize table" :ex-data %}))))
    :fetch-fn (fn [{:as opts :keys [describe-fn offset path]} xs]
                ;; TODO: use budget per row for table
                ;; TODO: opt out of eliding cols
                (cond (:error xs) (update xs :ex-data describe-fn opts [])
                      (seq path) (describe-fn (:rows xs) opts [])
                      :else (-> (cond-> (update xs :rows describe-fn (dissoc opts :!budget) [])
                                  (pos? offset) :rows)
                                (assoc :path [:rows] :replace-path [offset])
                                (dissoc :nextjournal/viewers))))}
   {:name :table-error :render-fn (quote v/table-error) :fetch-opts {:n 1}}
   {:name :clerk/markdown-block :transform-fn (fn [{:keys [doc text]}] (cond doc (with-md-viewer doc) text (with-viewer :markdown text)))}
   {:name :clerk/code-block :transform-fn #(with-viewer (if (:fold? %) :code-folded :code) (:text %))}
   {:name :tagged-value :render-fn '(fn [{:keys [tag value space?]}] (v/html (v/tagged-value {:space? space?} (str "#" tag) [v/inspect value])))
    :fetch-fn (fn [{:as opts :keys [describe-fn]} x]
                (update x :value describe-fn opts))}
   {:name :clerk/result :render-fn (quote v/result-viewer) :fetch-fn fetch-all}
   {:name :clerk/notebook
    :fetch-fn fetch-all
    :render-fn (quote v/notebook-viewer)
    :transform-fn #?(:cljs identity
                     :clj (fn [{:as doc :keys [ns]}]
                            (-> doc
                                (update :blocks (partial into [] (mapcat (partial with-block-viewer doc))))
                                (select-keys [:blocks :toc :title])
                                (cond-> ns (assoc :scope (datafy-scope ns))))))}
   {:name :hide-result :transform-fn (fn [_] nil)}])

(def markdown-viewers
  [{:name :nextjournal.markdown/doc :transform-fn (into-markup [:div.viewer-markdown])}

   ;; blocks
   {:name :nextjournal.markdown/heading
    :transform-fn (into-markup
                   (fn [{:as node :keys [heading-level]}]
                     [(str "h" heading-level) {:id (uri.normalize/normalize-fragment (md.transform/->text node))}]))}
   {:name :nextjournal.markdown/image :transform-fn #(with-viewer :html [:img (:attrs %)])}
   {:name :nextjournal.markdown/blockquote :transform-fn (into-markup [:blockquote])}
   {:name :nextjournal.markdown/paragraph :transform-fn (into-markup [:p])}
   {:name :nextjournal.markdown/ruler :transform-fn (into-markup [:hr])}
   {:name :nextjournal.markdown/code
    :transform-fn #(with-viewer :html
                     [:div.viewer-code
                      (with-viewer :code
                        (md.transform/->text %))])}

   ;; marks
   {:name :nextjournal.markdown/em :transform-fn (into-markup [:em])}
   {:name :nextjournal.markdown/strong :transform-fn (into-markup [:strong])}
   {:name :nextjournal.markdown/monospace :transform-fn (into-markup [:code])}
   {:name :nextjournal.markdown/strikethrough :transform-fn (into-markup [:s])}
   {:name :nextjournal.markdown/link :transform-fn (into-markup #(vector :a (:attrs %)))}
   {:name :nextjournal.markdown/internal-link :transform-fn (into-markup #(vector :a {:href (str "#" (:text %))}))}
   {:name :nextjournal.markdown/hashtag :transform-fn (into-markup #(vector :a {:href (str "#" (:text %))}))}

   ;; inlines
   {:name :nextjournal.markdown/text :transform-fn (into-markup [:span])}
   {:name :nextjournal.markdown/softbreak :transform-fn (fn [_] (with-viewer :html [:span " "]))}
   #?(:clj {:name :nextjournal.markdown/inline :transform-fn (comp eval read-string md.transform/->text)})

   ;; formulas
   {:name :nextjournal.markdown/formula :transform-fn :text :render-fn '(fn [tex] (v/katex-viewer tex {:inline? true}))}
   {:name :nextjournal.markdown/block-formula :transform-fn :text :render-fn 'v/katex-viewer}

   ;; lists
   {:name :nextjournal.markdown/bullet-list :transform-fn (into-markup [:ul])}
   {:name :nextjournal.markdown/numbered-list :transform-fn (into-markup [:ol])}
   {:name :nextjournal.markdown/todo-list :transform-fn (into-markup [:ul.contains-task-list])}
   {:name :nextjournal.markdown/list-item :transform-fn (into-markup [:li])}
   {:name :nextjournal.markdown/todo-item
    :transform-fn (into-markup (fn [{:keys [attrs]}] [:li [:input {:type "checkbox" :default-checked (:checked attrs)}]]))}

   ;; tables
   {:name :nextjournal.markdown/table :transform-fn (into-markup [:table])}
   {:name :nextjournal.markdown/table-head :transform-fn (into-markup [:thead])}
   {:name :nextjournal.markdown/table-body :transform-fn (into-markup [:tbody])}
   {:name :nextjournal.markdown/table-row :transform-fn (into-markup [:tr])}
   {:name :nextjournal.markdown/table-header
    :transform-fn (into-markup #(vector :th {:style (md.transform/table-alignment (:attrs %))}))}
   {:name :nextjournal.markdown/table-data
    :transform-fn (into-markup #(vector :td {:style (md.transform/table-alignment (:attrs %))}))}

   ;; ToC via [[TOC]] placeholder ignored
   {:name :nextjournal.markdown/toc :transform-fn (into-markup [:div.toc])}

   ;; sidenotes
   {:name :nextjournal.markdown/sidenote
    :transform-fn (into-markup (fn [{:keys [attrs]}] [:span.sidenote [:sup {:style {:margin-right "3px"}} (-> attrs :ref inc)]]))}
   {:name :nextjournal.markdown/sidenote-ref
    :transform-fn (into-markup [:sup.sidenote-ref])}])

(defn make-default-viewers []
  {:root (into default-viewers markdown-viewers)})

(defonce
  ^{:doc "atom containing a map of `:root` and per-namespace viewers."}
  !viewers
  (#?(:clj atom :cljs ratom/atom) (make-default-viewers)))

#_(reset! !viewers (make-default-viewers))

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


(defn find-viewer [viewers select-fn]
  (first (filter select-fn viewers)))

#_(find-viewer default-viewers (comp #{string?} :pred))
#_(find-viewer default-viewers (comp #{:elision} :name))

(defn find-named-viewer [viewers viewer-name]
  (find-viewer viewers (comp #{viewer-name} :name)))

#_(find-named-viewer default-viewers :elision)

(defn viewer-for [viewers x]
  (or (when-let [selected-viewer (->viewer x)]
        (if (keyword? selected-viewer)
          (or (find-named-viewer viewers selected-viewer)
              (throw (ex-info (str "cannot find viewer named " selected-viewer)
                              {:viewer-name selected-viewer :x (->value x) :viewers viewers})))
          selected-viewer))
      (find-viewer viewers (let [v (->value x)] (fn [{:keys [pred]}]
                                                (and (ifn? pred) (pred v)))))
      (throw (ex-info (str "cannot find matching viewer for value")
                      {:x x :value (->value x) :viewers viewers}))))

#_(viewer-for default-viewers [1 2 3])
#_(viewer-for default-viewers 42)
#_(viewer-for default-viewers (with-viewer :html [:h1 "Hello Hiccup"]))
#_(viewer-for default-viewers (with-viewer {:transform-fn identity} [:h1 "Hello Hiccup"]))


(defn wrapped-with-viewer
  ([x] (wrapped-with-viewer x default-viewers))
  ([x viewers]
   (let [{:as viewer :keys [render-fn transform-fn update-viewers-fn]} (viewer-for viewers x)
         opts (when (wrapped-value? x)
                (select-keys x [:nextjournal/width]))
         v (cond-> (->value x) transform-fn transform-fn)]
     (if (and transform-fn (not render-fn))
       (recur v (cond-> viewers update-viewers-fn update-viewers-fn))
       (cond-> (wrap-value v viewer)
         (seq opts) (merge opts))))))

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


(declare assign-closing-parens)

(defn process-render-fn [{:as viewer :keys [render-fn]}]
  (cond-> viewer
    (and render-fn (not (viewer-fn? render-fn)))
    (update :render-fn ->viewer-fn)))

(defn process-viewer [viewer]
  (if-not (map? viewer)
    viewer
    (-> viewer
        (dissoc :pred :transform-fn :fetch-fn :update-viewers-fn)
        process-render-fn)))

#_(process-viewer {:render-fn '(v/html [:h1]) :fetch-fn fetch-all})

(defn make-elision [fetch-opts viewers]
  (-> (with-viewer :elision fetch-opts)
      (wrapped-with-viewer viewers)
      (update :nextjournal/viewer process-viewer)))

#_(make-elision {:n 20} default-viewers)

(defn describe
  "Returns a subset of a given `value`."
  ([xs]
   (describe xs {}))
  ([xs opts]
   (assign-closing-parens
    (describe xs (merge {:!budget (atom (:budget opts 200)) :path [] :viewers (get-viewers *ns* (->viewers xs))} opts) [])))
  ([xs opts current-path]
   (let [{:as opts :keys [!budget viewers path offset]} (merge {:offset 0} opts)
         wrapped-value (wrapped-with-viewer xs viewers)
         {:as viewer :keys [fetch-opts fetch-fn update-viewers-fn]} (->viewer wrapped-value)
         {:as opts :keys [viewers]} (cond-> opts
                                      update-viewers-fn (update :viewers update-viewers-fn))
         fetch-opts (merge fetch-opts (select-keys opts [:offset :viewers]))
         descend? (< (count current-path)
                     (count path))
         xs (->value wrapped-value)]
     #_(prn :xs xs :type (type xs) :path path :current-path current-path :descend? descend? :fetch-fn? (some? fetch-fn))
     (when (and !budget (not descend?) (not fetch-fn))
       (swap! !budget #(max (dec %) 0)))
     (merge {:path path}
            (dissoc wrapped-value [:nextjournal/value :nextjournal/viewer])
            (with-viewer (process-viewer viewer)
              (cond fetch-fn
                    (fetch-fn (merge opts fetch-opts {:describe-fn describe}) xs)

                    descend?
                    (let [idx (first (drop (count current-path) path))]
                      (describe (cond (or (map? xs) (set? xs)) (nth (seq (ensure-sorted xs)) idx)
                                      (associative? xs) (get xs idx)
                                      (sequential? xs) (nth xs idx))
                                opts
                                (conj current-path idx)))

                    (string? xs)
                    (-> (if (and (number? (:n fetch-opts)) (< (:n fetch-opts) (count xs)))
                          (let [offset (opts :offset 0)
                                total (count xs)
                                new-offset (min (+ offset (:n fetch-opts)) total)
                                remaining (- total new-offset)]
                            (cond-> [(subs xs offset new-offset)]
                              (pos? remaining) (conj (make-elision {:path path :count total :offset new-offset :remaining remaining} viewers))
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

                        (conj (make-elision (cond-> (assoc count-opts :offset (inc offset) :path path)
                                              count (assoc :remaining (- count (inc offset)))) viewers))))

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
  (let [x (->value desc)
        viewer (->viewer desc)]
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
                   (throw (ex-info "paths mismatch" {:path-from-value path-from-value :path-from-more path-from-more :root root :more more :path-to-value (path-to-value (:path more)) :value value})))
                 (into (pop value) (:nextjournal/value more))))))


(defn assign-closing-parens
  ([node] (assign-closing-parens '() node))
  ([closing-parens node]
   (let [value (->value node)
         viewer (->viewer node)
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

#?(:clj
   (defn set-viewers!
     ([viewers] (set-viewers! *ns* viewers))
     ([scope viewers]
      (assert (or (#{:root} scope)
                  (instance? clojure.lang.Namespace scope)
                  (var? scope)))
      (swap! !viewers assoc scope viewers)
      viewers)))

(defn normalize-viewer-opts [opts]
  (set/rename-keys opts {:nextjournal.clerk/viewer :nextjournal/viewer
                         :nextjournal.clerk/viewers :nextjournal/viewers
                         :nextjournal.clerk/width :nextjournal/width}))

(defn normalize-viewer [viewer]
  (if (or (keyword? viewer)
          (map? viewer))
    viewer
    {:render-fn viewer}))

#_(normalize-viewer '#(v/html [:h3 "Hello " % "!"]))
#_(normalize-viewer :latex)
#_(normalize-viewer {:render-fn '#(v/html [:h3 "Hello " % "!"]) :transform-fn identity})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; public api

(defn with-viewer
  "Wraps the given value `x` and associates it with the given `viewer`. Takes an optional second `viewer-opts` arg."
  ([viewer x] (with-viewer viewer {} x))
  ([viewer viewer-opts x]
   (merge (normalize-viewer-opts viewer-opts)
          (-> x
              wrap-value
              (assoc :nextjournal/viewer (normalize-viewer viewer))))))

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
(def table        (partial with-viewer :table))
(def tex          (partial with-viewer :latex))
(def hide-result  (partial with-viewer :hide-result))
(def notebook     (partial with-viewer :clerk/notebook))
(defn doc-url [path]
  (->viewer-eval (list 'v/doc-url path)))
(def code (partial with-viewer :code))
