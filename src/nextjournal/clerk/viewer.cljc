(ns nextjournal.clerk.viewer
  (:refer-clojure :exclude [var?])
  (:require [clojure.string :as str]
            [clojure.pprint :as pprint]
            [clojure.datafy :as datafy]
            [clojure.set :as set]
            [flatland.ordered.map :as omap :refer [ordered-map]]
            #?@(:clj [[babashka.fs :as fs]
                      [clojure.repl :refer [demunge]]
                      [clojure.tools.reader :as tools.reader]
                      [editscript.edit]
                      [nextjournal.clerk.config :as config]
                      [nextjournal.clerk.analyzer :as analyzer]]
                :cljs [[goog.crypt]
                       [goog.crypt.Sha1]
                       [reagent.ratom :as ratom]
                       [sci.core :as sci]
                       [sci.impl.vars]
                       [sci.lang]
                       [applied-science.js-interop :as j]])
            [nextjournal.clerk.parser :as parser]
            [nextjournal.clerk.walk :as w]
            [nextjournal.markdown :as md]
            [nextjournal.markdown.utils :as md.utils]
            [nextjournal.markdown.transform :as md.transform])
  #?(:clj (:import (com.pngencoder PngEncoder)
                   (clojure.lang IDeref IAtom)
                   (java.lang Throwable)
                   (java.awt.image BufferedImage)
                   (java.util Base64)
                   (java.net URI URL)
                   (java.nio.file Files StandardOpenOption)
                   (javax.imageio ImageIO))))

(defrecord RenderFn [form #?(:cljs f)]
  #?@(:cljs [IFn
             (-invoke [_] (@f))
             (-invoke [_ x] (@f x))
             (-invoke [_ x y] (@f x y))]))

;; Make sure `RenderFn` is changed atomically
#?(:clj
   (extend-protocol editscript.edit/IType
     RenderFn
     (get-type [_] :val)))

(defn render-fn? [x]
  (instance? RenderFn x))

(defn render-eval? [x]
  (and (render-fn? x)
       (some? (:eval x))))

(defn resolve-symbol-alias [aliases sym]
  (if-let [full-ns (some->> sym namespace symbol (get aliases) str)]
    (symbol full-ns (name sym))
    sym))

#_(resolve-symbol-alias {'v (find-ns 'nextjournal.clerk.viewer)} 'nextjournal.clerk.render/render-code)

(defn resolve-aliases
  #?(:clj ([form] (resolve-aliases (ns-aliases *ns*) form)))
  ([aliases form] (w/postwalk #(cond->> % (qualified-symbol? %) (resolve-symbol-alias aliases)) form)))

(defn ->render-fn [form]
  (map->RenderFn {:form form
                  #?@(:cljs [:f (let [bound-eval *eval*]
                                  (delay (bound-eval form)))])}))

(defn ->render-fn+opts
  ([opts+form] (->render-fn+opts (first opts+form) (second opts+form)))
  ([opts form]
   (merge (->render-fn form) (dissoc opts :form))))

(defn ->render-eval [form]
  (->render-fn+opts {:eval true} form))

(def ^{:deprecated "0.18"} ->viewer-eval
  "Use `->render-eval` instead."
  ->render-eval)

(defn open-graph-metas [open-graph-properties]
  (into (list [:meta {:name "twitter:card" :content "summary_large_image"}])
        (map (fn [[prop content]] [:meta {:property (str "og:" (name prop)) :content content}]))
        open-graph-properties))

#?(:clj
   (defmethod print-method RenderFn [v ^java.io.Writer w]
     (.write w (if-let [opts (not-empty (dissoc (into {} v) :f :form))]
                 (str "#clerk/render-fn+opts " [opts (:form v)])
                 (str "#clerk/render-fn " (:form v))))))
#?(:cljs
   (defn ordered-map-reader-cljs [coll]
     (omap/ordered-map (vec coll))))

(def data-readers
  {'clerk/render-fn ->render-fn
   'clerk/render-fn+opts ->render-fn+opts
   'clerk/unreadable-edn eval
   'ordered/map #?(:clj omap/ordered-map-reader-clj
                   :cljs ordered-map-reader-cljs)})

#_(binding [*data-readers* {'render-fn ->render-fn}]
    (read-string (pr-str (->render-fn '(fn [x] x)))))
#_(binding [*data-readers* {'render-fn ->render-fn}]
    (read-string (pr-str (->render-fn 'number?))))

(comment
  (def num? (->render-fn 'number?))
  (num? 42)
  (:form num?)
  (pr-str num?))

(defn wrapped-value?
  "Tests if `x` is a map containing a `:nextjournal/value`."
  [x]
  (and (map? x) ;; can throw for `sorted-map`
       (try (contains? x :nextjournal/value)
            (catch #?(:clj Exception :cljs js/Error) _e false))))

(defn ensure-wrapped
  "Ensures `x` is wrapped in a map under a `:nextjournal/value` key."
  ([x] (if (wrapped-value? x) x {:nextjournal/value x}))
  ([x v] (-> x ensure-wrapped (assoc :nextjournal/viewer v))))

#_(ensure-wrapped 123)
#_(ensure-wrapped {:nextjournal/value 456})

(defn ->value
  "Takes `x` and returns the `:nextjournal/value` from it, or otherwise `x` unmodified."
  [x]
  (if (wrapped-value? x)
    (:nextjournal/value x)
    x))

#_(->value (with-viewer `code-viewer '(+ 1 2 3)))
#_(->value 123)

(defn ->viewer
  "Returns the `:nextjournal/viewer` for a given wrapped value `x`, `nil` otherwise."
  [x]
  (when (wrapped-value? x)
    (:nextjournal/viewer x)))


#_(->viewer (with-viewer `code-viewer '(+ 1 2 3)))
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

(defn css-class
  "Returns the `:nextjournal/css-class` for a given wrapped value `x`, `nil` otherwise."
  [x]
  (when (wrapped-value? x)
    (:nextjournal/css-class x)))

(def viewer-opts-normalization
  "Normalizes ns for viewer opts keywords `:nextjournal.clerk/x` => `:nextjournal/x`"
  (into {:nextjournal/opts :nextjournal/render-opts}
        (map (juxt #(keyword "nextjournal.clerk" (name %))
                   #(keyword "nextjournal" (name %))))
        (conj parser/block-settings :viewer :viewers)))

(defn throw-when-viewer-opts-invalid [opts]
  (when-not (map? opts)
    (throw (ex-info "normalize-viewer-opts not passed `map?` opts" {:opts opts})))
  (when-let [width (:nextjournal/width opts)]
    (when-not (contains? #{:full :wide :prose} width)
      (throw (ex-info "Invalid `:nextjournal.clerk/width`, allowed values are `:full`, `:wide` and `:prose`." {:width width})))
    (when-let [css-class (:nextjournal/css-class opts)]
      (throw (ex-info "Conflicting viewer options `:nextjournal.clerk/width` and `:nextjournal.clerk/css-class`. Please remove either one."
                      {:width width :css-class css-class}))))
  opts)

(defn normalize-viewer-opts [viewer-opts]
  (throw-when-viewer-opts-invalid (set/rename-keys viewer-opts viewer-opts-normalization)))

(defn normalize-viewer [viewer]
  (cond (keyword? viewer) viewer
        (symbol? viewer) viewer
        (map? viewer) viewer
        (seq? viewer) {:render-fn viewer}
        #?@(:clj [(fn? viewer) {:transform-fn viewer}])
        :else (throw (ex-info (str "cannot normalize viewer `" viewer "`" ) {:viewer viewer}))))

#_(normalize-viewer '#(vector :h3 "Hello " % "!"))
#_(normalize-viewer :latex)
#_(normalize-viewer 'katex-viewer)
#_(normalize-viewer {:render-fn '#(vector :h3 "Hello " % "!") :transform-fn identity})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; public api

(defn with-viewer
  "Wraps the given value `x` and associates it with the given `viewer`. Takes an optional second `viewer-opts` arg."
  ([viewer x] (with-viewer viewer nil x))
  ([viewer viewer-opts x]
   (merge (when viewer-opts (normalize-viewer-opts viewer-opts))
          (cond-> (ensure-wrapped x)
            (not (and (map? viewer) (empty? viewer)))
            (assoc :nextjournal/viewer (normalize-viewer viewer))))))

;; TODO: Think of a better name
(defn with-viewer-extracting-opts [viewer & opts+items]
  ;; TODO: maybe support sequantial & viewer-opts?
  (cond
    (and (map? (first opts+items))
         (not (wrapped-value? (first opts+items)))
         (seq (set/intersection parser/block-settings (set (keys (first opts+items))) )))
    (with-viewer viewer (first opts+items) (rest opts+items))

    (and (sequential? (first opts+items)) (= 1 (count opts+items)))
    (apply (partial with-viewer viewer) opts+items)

    :else
    (with-viewer viewer opts+items)))

#_(with-viewer `latex-viewer "x^2")
#_(with-viewer '#(vector :h3 "Hello " % "!") "x^2")

(defn with-viewers
  "Binds viewers to types, eg {:boolean view-fn}"
  [viewers x]
  (-> x
      ensure-wrapped
      (assoc :nextjournal/viewers viewers)))

(def ^:dynamic *viewers* nil)

(defmacro with-viewers* [vs & body] `(binding [*viewers* ~vs] ~@body))

#_(->> "x^2" (with-viewer `latex-viewer) (with-viewers [{:name `latex-viewer :render-fn `mathjax-viewer}]))

(defn get-safe
  ([key] #(get-safe % key))
  ([map key]
   (when (map? map)
     (try (get map key) ;; can throw for e.g. sorted-map
          (catch #?(:clj Exception :cljs js/Error) _e nil)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; table viewer normalization

(defn rpad-vec [v length padding]
  (vec (take length (concat v (repeat padding)))))

(def missing-pred
  :nextjournal/missing)

(defn count-bounded [xs]
  (bounded-count #?(:clj config/*bounded-count-limit* :cljs 10000) xs))

(defn normalize-seq-of-seq [s]
  (let [max-count (count (apply max-key count (take 1000 s)))]
    {:rows (map #(rpad-vec (->value %) max-count missing-pred) s)}))

(defn normalize-seq-of-map [s]
  ;; currently considering the first 1000 rows for the columns
  ;; we can't use every row as it would realize infinte sequences
  ;; TODO: allow customisation
  (let [ks (->> s (take 1000) (mapcat keys) distinct vec)]
    {:head ks
     :rows (map (fn [m] (mapv #(get m % missing-pred) ks)) s)}))

(defn normalize-map-of-seq [m]
  (let [ks (-> m keys vec)]
    {:head ks
     :rows (map-indexed (fn [i _] (map (fn [k] (nth (get m k) i missing-pred)) ks))
                        (val (apply max-key (comp count-bounded val) m)))}))

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
    (and (map? data) (-> data (get-safe :rows) sequential?)) (normalize-seq-to-vec data)
    (and (map? data) (sequential? (first (vals data)))) (normalize-map-of-seq data)
    (and (sequential? data) (map? (first data))) (normalize-seq-of-map data)
    (and (sequential? data) (sequential? (first data))) (normalize-seq-of-seq data)
    (empty? data) {:rows []}
    :else nil))

(defn demunge-ex-data [ex-data]
  (cond-> ex-data
    (map? ex-data)
    (update :trace (fn [traces] (mapv #(update % 0 (comp demunge pr-str)) traces)))))

#_(demunge-ex-data (datafy/datafy (ex-info "foo" {:bar :baz})))

(declare present present* !viewers apply-viewers apply-viewers* ensure-wrapped-with-viewers process-viewer process-wrapped-value default-viewers find-named-viewer)

(defn inspect-fn []
  #?(:clj (->render-eval 'nextjournal.clerk.render/inspect-presented)
     :cljs (eval 'nextjournal.clerk.render/inspect-presented)))

(defn mark-presented [wrapped-value]
  (assoc wrapped-value :nextjournal/presented? true))

(defn mark-preserve-keys
  ([wrapped-value]
   (mark-preserve-keys #{} wrapped-value))
  ([preserve-keys-fn wrapped-value]
   (assoc wrapped-value :nextjournal/preserve-keys-fn preserve-keys-fn)))

(defn preserve-keys [preserve-keys-fn]
  (partial mark-preserve-keys preserve-keys-fn))

;; exploits the fact that that our keyword renderer doesn't show spaces
(let [kw (keyword "nextjournal/value ")]
  (defn inspect-wrapped-values
    "Takes `x` and modifies it such that Clerk will show raw
  wrapped-values. Useful for inspecting the inner workings of the
  viewer api. Also useable as a `:transform-fn`.

  Will eagerly walk the whole data structure so unsuited for infinite
  sequences."
    [x]
    (w/postwalk-replace {:nextjournal/value kw} x)))

(defn fetch-all [_opts _xs]
  (throw (ex-info "`fetch-all` is deprecated, please use a `:transform-fn` with `mark-presented` instead." {})))

(def datafied?
  (get-safe :nextjournal.clerk/datafied))

(defn with-md-viewer [wrapped-value]
  (let [{:as _node :keys [type]} (->value wrapped-value)]
    (when-not type
      (throw (ex-info "no type given for with-md-viewer" {:wrapped-value wrapped-value})))
    (with-viewer (keyword "nextjournal.markdown" (name type)) wrapped-value)))

(defn apply-viewers-to-md [viewers doc x]
  (-> (ensure-wrapped-with-viewers viewers (assoc x ::doc doc))
      (with-md-viewer)
      (apply-viewers)
      (as-> w
          (if (= `markdown-node-viewer (:name (->viewer w)))
            (->value w)
            [(inspect-fn) (process-wrapped-value w)]))))

(defn into-markup [markup]
  (fn [{:as wrapped-value :nextjournal/keys [viewers render-opts]}]
    (-> (with-viewer {:name `markdown-node-viewer :render-fn 'identity} wrapped-value)
        mark-presented
        (update :nextjournal/value
                (fn [{:as node :keys [text content] ::keys [doc]}]
                  (into (cond-> markup (fn? markup) (apply [(merge render-opts node)]))
                        (cond text [text]
                              content (mapv (partial apply-viewers-to-md viewers doc) content))))))))

;; A hack for making Clerk not fail in the precense of
;; programmatically generated keywords or symbols that cannot be read.
;;
;; Unfortunately there's currently no solution to override
;; `print-method` locally, see
;; https://clojurians.slack.com/archives/C03S1KBA2/p1667334982789659

#?(:clj (defn roundtrippable? [x]
          (try
            (= x (-> x str tools.reader/read-string))
            (catch Exception _e false))))

#?(:clj
   (defmethod print-method clojure.lang.Keyword [o w]
     (if (roundtrippable? o)
       (print-simple o w)
       (.write w (str "#clerk/unreadable-edn "
                      (pr-str (if-let [ns (namespace o)]
                                (list 'keyword ns (name o))
                                (list 'keyword (name o)))))))))

#?(:clj
   (defmethod print-method clojure.lang.Symbol [o w]
     (if (or (roundtrippable? o)
             (= (name o) "?@"))  ;; splicing reader conditional, see issue #338
       (print-simple o w)
       (.write w (str "#clerk/unreadable-edn "
                      (pr-str (if-let [ns (namespace o)]
                                (list 'symbol ns (name o))
                                (list 'symbol (name o)))))))))

#?(:clj
   (defn ->edn [x]
     (binding [*print-namespace-maps* false
               *print-length* nil
               *print-level* nil]
       (pr-str x))))

#_(->edn {:nextjournal/value :foo})
#_(->edn {(keyword "with spaces") :foo})

(defn update-val [f & args]
  (fn [wrapped-value] (apply update wrapped-value :nextjournal/value f args)))

#_((update-val + 1) {:nextjournal/value 41})

(defn var? [x]
  (or (clojure.core/var? x)
      #?(:cljs (instance? sci.lang.Var x))))

(defn var-from-def? [x]
  (var? (get-safe x :nextjournal.clerk/var-from-def)))

(def var-from-def-viewer
  {:name `var-from-def-viewer
   :pred var-from-def?
   :transform-fn (update-val (some-fn :nextjournal.clerk/var-snapshot
                                      (comp deref :nextjournal.clerk/var-from-def)))})

(defn apply-viewer-unwrapping-var-from-def
  "Applies the `viewer` (if set) to the given result `result`. In case
  the `value` is a `var-from-def?` it will be unwrapped unless the
  viewer opts out with a truthy `:nextjournal.clerk/var-from-def`."
  [{:as result :nextjournal/keys [value viewer]}]
  (if viewer
    (let [value+viewer (if (or (var? viewer) (fn? viewer))
                         (viewer value)
                         {:nextjournal/value value
                          :nextjournal/viewer (normalize-viewer viewer)})
          {unwrap-var :transform-fn var-from-def? :pred} var-from-def-viewer]
      (assoc result :nextjournal/value (cond-> value+viewer
                                         (and (var-from-def? value)
                                              (-> value+viewer ->viewer :var-from-def? not))
                                         unwrap-var)))
    result))

#?(:clj
   (defn data-uri-base64-encode [x content-type]
     (str "data:" content-type ";base64," (.encodeToString (Base64/getEncoder) x))))

#?(:clj
   (defn store+get-cas-url! [{:keys [out-path ext]} content]
     (assert out-path)
     (let [cas-url (str "_data/" (analyzer/sha2-base58 content) (when ext ".") ext)
           cas-path (fs/path out-path cas-url)]
       (fs/create-dirs (fs/parent cas-path))
       (when-not (fs/exists? cas-path)
         (Files/write cas-path content (into-array [StandardOpenOption/CREATE])))
       cas-url)))

#?(:clj
   (def index-path? (partial re-matches #"index.(cljc?|md)")))

#?(:clj
   (defn relative-root-prefix-from [path]
     (str "./" (when-not (index-path? path)
                 (str/join (repeat (inc (get (frequencies (str path)) \/ 0))
                                   "../"))))))

#?(:clj
   (defn map-index [{:as _opts :keys [index]} path]
     (get {index "index.clj"} path path)))

#?(:clj
   (defn maybe-store-result-as-file [{:as doc+blob-opts :keys [out-path file]} {:as result :nextjournal/keys [content-type value]}]
     ;; TODO: support customization via viewer api
     (if-let [image-type (second (re-matches #"image/(\w+)" content-type))]
       (assoc result :nextjournal/value
              (str (relative-root-prefix-from (map-index doc+blob-opts file))
                   (store+get-cas-url! (assoc doc+blob-opts :ext image-type) value)))
       result)))

#_(nextjournal.clerk.builder/build-static-app! {:paths ["image.clj" "notebooks/image.clj" "notebooks/viewers/image.clj"] :browse? false})

#?(:clj
   (defn process-blobs [{:as doc+blob-opts :keys [blob-mode blob-id]} presented-result]
     (w/postwalk #(if-some [content-type (get-safe % :nextjournal/content-type)]
                    (case blob-mode
                      :lazy-load (assoc % :nextjournal/value {:blob-id blob-id :path (:path %)})
                      :inline (update % :nextjournal/value data-uri-base64-encode content-type)
                      :file (maybe-store-result-as-file doc+blob-opts %))
                    %)
                 presented-result)))

(defn get-default-viewers []
  (:default @!viewers default-viewers))

(defn datafy-scope [scope]
  (cond
    #?@(:clj [(instance? clojure.lang.Namespace scope) (ns-name scope)]
        :cljs [(instance? sci.lang.Namespace scope) (sci/ns-name scope)])
    (symbol? scope) scope
    (#{:default} scope) scope
    :else (throw (ex-info (str "Unsupported scope `" scope "`. Valid scopes are namespaces, symbol namespace names or `:default`.")
                          {:scope scope}))))
(defn get-*ns* []
  (or *ns* #?(:cljs @sci.core/ns)))

(defn get-viewers
  ([scope] (get-viewers scope nil))
  ([scope value]
   (or (when value (->viewers value))
       *viewers*
       (when scope (@!viewers (datafy-scope scope)))
       (get-default-viewers))))

#_(get-viewers nil nil)

(defn fragment [& xs]
  {:nextjournal.clerk/fragment (if (and (sequential? (first xs)) (= 1 (count xs))) (first xs) xs)})

(declare result-viewer ->opts)

(defn ^:private processed-block-id
  ([block-id] (processed-block-id block-id []))
  ([block-id path] (str block-id (when (and (seq path)
                                            (not= [0] path))
                                   (str "-" (str/join "-" path))))))

(defn transform-result [{:as wrapped-value :keys [path]}]
  (let [{:as cell :keys [form id settings result] ::keys [fragment-item? doc]} (:nextjournal/value wrapped-value)
        {:keys [package]} doc
        {:nextjournal/keys [value blob-id viewers]} result
        blob-mode (cond
                    (= :single-file package) :inline
                    (= :directory package) :file
                    blob-id :lazy-load)
        #?(:clj blob-opts :cljs _) (assoc doc :blob-mode blob-mode :blob-id blob-id)
        opts-from-block (-> settings
                            (select-keys (keys viewer-opts-normalization))
                            (set/rename-keys viewer-opts-normalization))
        {:as to-present :nextjournal/keys [auto-expand-results?]} (merge (dissoc (->opts wrapped-value) :!budget :nextjournal/budget)
                                                                         (dissoc cell :result ::doc) ;; TODO: reintroduce doc once we know why it OOMs the static build on CI (some walk issue probably)
                                                                         opts-from-block
                                                                         (ensure-wrapped-with-viewers (or viewers (get-viewers (get-*ns*))) value))
        presented-result (-> (present to-present)
                             (update :nextjournal/render-opts
                                     (fn [{:as opts existing-id :id}]
                                       (cond-> opts
                                         auto-expand-results? (assoc :auto-expand-results? auto-expand-results?)
                                         fragment-item? (assoc :fragment-item? true)
                                         (not existing-id) (assoc :id (processed-block-id (str id "-result") path)))))
                             #?(:clj (->> (process-blobs blob-opts))))
        render-eval-result? (-> presented-result :nextjournal/value render-eval?)]
    #_(prn :presented-result render-eval? presented-result)
    (-> wrapped-value
        mark-presented
        (merge {:nextjournal/value (cond-> {:nextjournal/presented presented-result :nextjournal/blob-id blob-id}
                                     render-eval-result?
                                     (assoc ::render-eval-form (-> presented-result :nextjournal/value :form))

                                     (-> form meta :nextjournal.clerk/open-graph :image)
                                     (assoc :nextjournal/open-graph-image-capture true)

                                     #?@(:clj [(= blob-mode :lazy-load)
                                               (assoc :nextjournal/fetch-opts {:blob-id blob-id}
                                                      :nextjournal/hash (analyzer/->hash-str [blob-id presented-result opts-from-block]))]))}
               (dissoc presented-result :nextjournal/value :nextjournal/viewer :nextjournal/viewers)))))

#_(nextjournal.clerk.view/doc->viewer @nextjournal.clerk.webserver/!doc)

(def hide-result-viewer
  {:name `hide-result-viewer :transform-fn (fn [_] nil)})

(defn ->visibility [{:as cell :keys [settings]}]
  (let [{:keys [code result]} (:nextjournal.clerk/visibility settings)]
    {:code? (not= :hide code)
     :result? (and (:result cell)
                   (or (not= :hide result)
                       (-> cell :result :nextjournal/value (get-safe :nextjournal/value) render-eval?)))}))

(defn hidden-render-eval-result? [{:keys [settings result]}]
  (and (= :hide (-> settings :nextjournal.clerk/visibility :result))
       (render-eval? (-> result :nextjournal/value (get-safe :nextjournal/value)))))

#_(->visibility {:settings {:nextjournal.clerk/visibility {:code :show :result :show}}})
#_(->visibility {:settings {:nextjournal.clerk/visibility {:code :fold :result :show}}})
#_(->visibility {:settings {:nextjournal.clerk/visibility {:code :fold :result :hide}}})

(defn process-sidenotes [cell-doc {:keys [footnotes]}]
  (if (seq footnotes)
    (md.utils/insert-sidenote-containers (assoc cell-doc :footnotes footnotes))
    cell-doc))

(defn process-image-source [src {:as doc :keys [file package]}]
  #?(:cljs src
     :clj  (cond
             (not (fs/exists? src)) src
             (= :directory package) (str (relative-root-prefix-from (map-index doc file))
                                         (store+get-cas-url! (assoc doc :ext (fs/extension src))
                                                             (fs/read-all-bytes src)))
             (= :single-file package) (data-uri-base64-encode (fs/read-all-bytes src) (Files/probeContentType (fs/path src)))
             :else (str "/_fs/" src))))

#?(:clj
   (defn read-image [image-or-url]
     (ImageIO/read
      (if (string? image-or-url)
        (URL. (cond->> image-or-url (not (.getScheme (URI. image-or-url))) (str "file:")))
        image-or-url))))

#?(:clj
   (defn image-width [image]
     (let [w (.getWidth image) h (.getHeight image) r (float (/ w h))]
       (if (and (< 2 r) (< 900 w)) :full :wide))))

(defn md-image->viewer [doc block-id idx {:keys [attrs]}]
  (with-viewer `html-viewer
    #?(:clj {:nextjournal/render-opts {:id (processed-block-id block-id [idx])}
             :nextjournal/width (try (image-width (read-image (:src attrs)))
                                     (catch Throwable _ :prose))})
    [:div.flex.flex-col.items-center.not-prose.mb-4
     [:img (update attrs :src process-image-source doc)]]))

#_(present @nextjournal.clerk.webserver/!doc)

(defn update-if [m k f] (if (k m) (update m k f) m))
#_(update-if {:n "42"} :n #(Integer/parseInt %))

(defn cell->code-block-viewer [{:as cell :keys [id]}]
  (with-viewer (if (= :fold (-> cell :settings :nextjournal.clerk/visibility :code))
                 `folded-code-block-viewer
                 `code-block-viewer)
    {:nextjournal/render-opts (-> cell (select-keys [:loc]) (assoc :id (processed-block-id (str id "-code"))))}
    (dissoc cell ::doc :result)))

(defn maybe-wrap-var-from-def [val form]
  (cond->> val
    (and (var? val) (parser/deflike? form))
    (hash-map :nextjournal.clerk/var-from-def)))

(defn fragment-seq
  ([cell] (fragment-seq (:form cell) cell))
  ([form {:as cell :keys [result]}]
   (if-some [fragment (-> result :nextjournal/value (get-safe :nextjournal.clerk/fragment))]
     (mapcat (fn [r i]
               (fragment-seq
                (when (seq? form) (get (vec form) (inc i)))
                (-> cell
                    (assoc ::fragment-item? true)
                    (assoc-in [:result :nextjournal/value] r))))
             fragment (range (count fragment)))
     (list (update-in cell [:result :nextjournal/value] maybe-wrap-var-from-def form)))))

(defn cell->result-viewer [cell]
  (-> cell
      (update-if :result apply-viewer-unwrapping-var-from-def)
      fragment-seq
      (->> (mapv (partial with-viewer
                          (cond-> result-viewer
                            (hidden-render-eval-result? cell)
                            (assoc :render-fn '(fn [_ _] [:<>]))))))))

(defn transform-cell [cell]
  (let [{:keys [code? result?]} (->visibility cell)]
    (cond-> []
      code?
      (conj (cell->code-block-viewer cell))
      result?
      (into (cell->result-viewer cell)))))

(defn cell-visible? [cell]
  (let [{:keys [code? result?]} (->visibility cell)]
    (or code? result?)))

(def cell-viewer
  {:name `cell-viewer
   :transform-fn (update-val transform-cell)
   :render-fn '(fn [xs opts] (into [:<>] (nextjournal.clerk.render/inspect-children opts) xs))})

(defn lift-block-images
  "Lift an image node to top-level when it is the only child of a paragraph."
  [md-nodes]
  (map (fn [{:as node :keys [type content]}]
         (if (and (= :paragraph type) (= 1 (count content)) (= :image (:type (first content))))
           (first content)
           node)) md-nodes))

(defn with-block-viewer [doc {:as cell :keys [type id]}]
  (case type
    :markdown (let [{:keys [content]} (:doc cell)
                    !idx (atom -1)]
                (mapcat (fn [fragment]
                          (if (= :image (:type (first fragment)))
                            (map #(md-image->viewer doc id (swap! !idx inc) %) fragment)
                            [(with-viewer `markdown-viewer {:nextjournal/render-opts {:id (processed-block-id id [(swap! !idx inc)])}}
                               (process-sidenotes {:type :doc
                                                   :content (vec fragment)
                                                   ::doc doc} doc))]))
                        (partition-by (comp #{:image} :type)
                                      (lift-block-images content))))

    :code (if (cell-visible? cell)
            [(with-viewer `cell-viewer (assoc cell ::doc doc))]
            [])))

#_(:blocks (:nextjournal/value (nextjournal.clerk.view/doc->viewer @nextjournal.clerk.webserver/!doc)))

#_(nextjournal.clerk.view/doc->viewer @nextjournal.clerk.webserver/!doc)

(defn update-viewers [viewers select-fn->update-fn]
  (reduce (fn [viewers [pred update-fn]]
            (mapv (fn [viewer]
                    (cond-> viewer
                      (pred viewer) update-fn)) viewers))
          viewers
          select-fn->update-fn))

#_(update-viewers default-viewers {:page-size #(dissoc % :page-size)})

(defn ^:private ->ordered-map-by-name [viewers]
  (into (ordered-map)
        (map (juxt :name identity))
        viewers))

(defn ^:private merge-prepending [m1 m2]
  (into (apply dissoc m2 (keys m1))
        (into m1 m2)))

#_(merge-prepending (ordered-map :bar 1 :baz 2) (ordered-map {:baz 3 :a 1}))

(defn ^:private merge-viewers [viewers added-viewers]
  (when-let [unnamed-viewers (not-empty (filter (complement :name) (concat viewers added-viewers)))]
    (throw (ex-info "every viewer must have a name" {:unnamed-viewers unnamed-viewers})))
  (vec (vals (merge-prepending (->ordered-map-by-name viewers)
                               (->ordered-map-by-name added-viewers)))))

(defn add-viewers
  ([added-viewers] (add-viewers (get-default-viewers) added-viewers))
  ([viewers added-viewers] (into (filterv (complement :name) (concat viewers added-viewers))
                                 (merge-viewers (filter :name viewers)
                                                (filter :name added-viewers)))))

#_(add-viewers (take 10 default-viewers)
               [{:pred number?
                 :render-fn '#(vector :div.inline-block {:style {:width 16 :height 16}
                                                         :class (if (pos? %) "bg-black" "bg-white border-solid border-2 border-black")})}])

(def table-missing-viewer
  {:name `table-missing-viewer
   :pred #{:nextjournal/missing}
   :render-fn '(fn [x] [:<>])})

(def table-markup-viewer
  {:name `table-markup-viewer
   :render-fn 'nextjournal.clerk.render.table/render-table-markup})

(def table-head-viewer
  {:name `table-head-viewer
   :render-fn 'nextjournal.clerk.render.table/render-table-head})

(def table-body-viewer
  {:name `table-body-viewer
   :render-fn 'nextjournal.clerk.render.table/render-table-body})

(def table-row-viewer
  {:name `table-row-viewer
   :render-fn 'nextjournal.clerk.render.table/render-table-row})

#?(:clj (def utc-date-format ;; from `clojure.instant/thread-local-utc-date-format`
          (doto (java.text.SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ss.SSS-00:00")
            (.setTimeZone (java.util.TimeZone/getTimeZone "GMT")))))

(def markdown-viewers
  [{:name :nextjournal.markdown/doc
    :transform-fn (into-markup (fn [{:keys [id]}] [:div.viewer.markdown-viewer.w-full.max-w-prose.px-8 {:data-block-id id}]))}
   {:name :nextjournal.markdown/heading
    :transform-fn (into-markup
                   (fn [{:keys [attrs heading-level]}]
                     [(str "h" heading-level) attrs]))}
   {:name :nextjournal.markdown/image
    :transform-fn (fn [{node :nextjournal/value}]
                    (with-viewer `html-viewer
                      [:img.inline (-> node :attrs (update :src process-image-source (::doc node)))]))}
   {:name :nextjournal.markdown/blockquote :transform-fn (into-markup [:blockquote])}
   {:name :nextjournal.markdown/paragraph :transform-fn (into-markup [:p])}
   {:name :nextjournal.markdown/plain :transform-fn (into-markup [:<>])}
   {:name :nextjournal.markdown/ruler :transform-fn (into-markup [:hr])}
   {:name :nextjournal.markdown/code
    :transform-fn (update-val #(with-viewer `html-viewer
                                 [:div.code-viewer.code-listing
                                  (with-viewer `code-viewer
                                    {:nextjournal/render-opts {:language (:language % "clojure")}}
                                    (str/trim-newline (md.transform/->text %)))]))}

   ;; marks
   {:name :nextjournal.markdown/em :transform-fn (into-markup [:em])}
   {:name :nextjournal.markdown/strong :transform-fn (into-markup [:strong])}
   {:name :nextjournal.markdown/monospace :transform-fn (into-markup [:code])}
   {:name :nextjournal.markdown/strikethrough :transform-fn (into-markup [:s])}
   {:name :nextjournal.markdown/link :transform-fn (into-markup #(vector :a (:attrs %)))}

   ;; inlines
   {:name :nextjournal.markdown/text :transform-fn (into-markup [:<>])}
   {:name :nextjournal.markdown/softbreak :transform-fn (fn [_] (with-viewer `html-viewer [:<> " "]))}
   {:name :nextjournal.markdown/hardbreak :transform-fn (fn [_] (with-viewer `html-viewer [:br]))}

   ;; formulas
   {:name :nextjournal.markdown/formula
    :transform-fn (comp :text ->value)
    :render-fn '(fn [tex] (nextjournal.clerk.render/render-katex tex {:inline? true}))}
   {:name :nextjournal.markdown/block-formula
    :transform-fn (comp :text ->value)
    :render-fn 'nextjournal.clerk.render/render-katex}

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
   {:name :nextjournal.markdown/sidenote-container
    :transform-fn (fn [{:as wrapped-value :nextjournal/keys [viewers render-opts]}]
                    (-> (with-viewer {:name `markdown-node-viewer :render-fn 'identity} wrapped-value)
                        mark-presented
                        (update :nextjournal/value
                                (fn [{:as node :keys [text content] ::keys [doc]}]
                                  [:div.sidenote-container
                                   (into [:div.sidenote-main-col]
                                         (map (partial apply-viewers-to-md viewers doc))
                                         (drop-last content))
                                   (apply-viewers-to-md viewers doc (last content))]))))}
   {:name :nextjournal.markdown/sidenote-column :transform-fn (into-markup [:div.sidenote-column])}
   {:name :nextjournal.markdown/sidenote
    :transform-fn (into-markup (fn [{:keys [ref]}]
                                 [:span.sidenote [:sup {:style {:margin-right "3px"}} (str (inc ref))]]))}
   {:name :nextjournal.markdown/sidenote-ref
    :transform-fn (fn [wrapped-value] (with-viewer `html-viewer [:sup.sidenote-ref (-> wrapped-value ->value :ref inc)]))}])

(def char-viewer
  {:name `char-viewer :pred char? :render-fn '(fn [c] [:span.cmt-string.inspected-value "\\" c])})

(def string-viewer
  {:name `string-viewer
   :pred string?
   :render-fn 'nextjournal.clerk.render/render-quoted-string
   :opening-paren "\""
   :closing-paren "\""
   :page-size 80})

(def number-viewer
  {:name `number-viewer
   :pred number?
   :render-fn 'nextjournal.clerk.render/render-number
   #?@(:clj [:transform-fn (update-val #(cond-> %
                                          (or (instance? clojure.lang.Ratio %)
                                              (instance? clojure.lang.BigInt %)
                                              (> % 9007199254740992)
                                              (< % -9007199254740992)) pr-str))])})

(def number-hex-viewer
  {:name `number-hex-viewer :render-fn '(fn [num] (nextjournal.clerk.render/render-number (str "0x" (.toString (js/Number. num) 16))))})

(def symbol-viewer
  {:name `symbol-viewer :pred symbol? :render-fn '(fn [x] [:span.cmt-keyword.inspected-value (str x)])})

(def keyword-viewer
  {:name `keyword-viewer :pred keyword? :render-fn '(fn [x] [:span.cmt-atom.inspected-value (str x)])})

(def nil-viewer
  {:name `nil-viewer :pred nil? :render-fn '(fn [_] [:span.cmt-default.inspected-value "nil"])})

(def boolean-viewer
  {:name `boolean-viewer :pred boolean? :render-fn '(fn [x] [:span.cmt-bool.inspected-value (str x)])})

(def map-entry-viewer
  {:name `map-entry-viewer :pred map-entry? :render-fn '(fn [xs opts] (into [:<>] (comp (nextjournal.clerk.render/inspect-children opts) (interpose " ")) xs)) :page-size 2})

(def read+inspect-viewer
  {:name `read+inspect-viewer :render-fn '(fn [x] (try [nextjournal.clerk.render/inspect (nextjournal.clerk.viewer/read-string-without-tag-table x)]
                                                       (catch js/Error _e
                                                         (nextjournal.clerk.render/render-unreadable-edn x))))})

(def vector-viewer
  {:name `vector-viewer :pred vector? :render-fn 'nextjournal.clerk.render/render-coll :opening-paren "[" :closing-paren "]" :page-size 20})

(def set-viewer
  {:name `set-viewer :pred set? :render-fn 'nextjournal.clerk.render/render-coll :opening-paren "#{" :closing-paren "}" :page-size 20})

(def sequential-viewer
  {:name `sequential-viewer :pred sequential? :render-fn 'nextjournal.clerk.render/render-coll :opening-paren "(" :closing-paren ")" :page-size 20})

(def map-viewer
  {:name `map-viewer :pred map? :render-fn 'nextjournal.clerk.render/render-map :opening-paren "{" :closing-paren "}" :page-size 10})

#?(:cljs (defn var->symbol [v] (if (instance? sci.lang.Var v) (sci.impl.vars/toSymbol v) (symbol v))))

(def var-viewer
  {:name `var-viewer
   :pred var?
   :transform-fn (comp #?(:cljs var->symbol :clj symbol) ->value)
   :render-fn '(fn [x] [:span.inspected-value [:span.cmt-meta "#'" (str x)]])})

(defn ->opts [wrapped-value]
  (select-keys wrapped-value [:nextjournal/budget :nextjournal/css-class :nextjournal/width :nextjournal/render-opts
                              :nextjournal/render-evaluator
                              :!budget :store!-wrapped-value :present-elision-fn :path :offset]))

(defn inherit-opts [{:as wrapped-value :nextjournal/keys [viewers]} value path-segment]
  (-> (ensure-wrapped-with-viewers viewers value)
      (merge (select-keys (->opts wrapped-value) [:!budget :store!-wrapped-value :present-elision-fn :nextjournal/budget :path]))
      (update :path (fnil conj []) path-segment)))

(defn present-ex-data [parent throwable-map]
  (let [present-child (fn [idx data] (present (inherit-opts parent data idx)))]
    (-> throwable-map
        (update-if :data (partial present-child 0))
        (update-if :via (fn [exs]
                          (mapv (fn [i ex] (update-if ex :data (partial present-child (inc i))))
                                (range (count exs))
                                exs))))))

(def throwable-viewer
  {:name `throwable-viewer
   :render-fn 'nextjournal.clerk.render/render-throwable
   :pred (fn [e] (instance? #?(:clj Throwable :cljs js/Error) e))
   :transform-fn (fn [wrapped-value]
                   (-> wrapped-value
                       mark-presented
                       (update :nextjournal/value (comp demunge-ex-data
                                                        (partial present-ex-data wrapped-value)
                                                        datafy/datafy))))})

#?(:clj
   (defn buffered-image->bytes [^BufferedImage image]
     (.. (PngEncoder.)
         (withBufferedImage image)
         (withCompressionLevel 1)
         (toBytes))))

(def image-viewer
  {#?@(:clj [:pred #(instance? BufferedImage %)
             :transform-fn (fn [{image :nextjournal/value}]
                             (-> {:nextjournal/value (buffered-image->bytes image)
                                  :nextjournal/content-type "image/png"
                                  :nextjournal/width (image-width image)}
                                 mark-presented))])
   :name `image-viewer
   :render-fn '(fn [blob-or-url] [:div.flex.flex-col.items-center.not-prose
                                  [:img {:src #?(:clj  (nextjournal.clerk.render/url-for blob-or-url)
                                                 :cljs blob-or-url)}]])})

(def ideref-viewer
  {:name `ideref-viewer
   :pred #(#?(:clj instance? :cljs satisfies?) IDeref %)
   :transform-fn (update-val (fn [ideref]
                               (with-viewer `tagged-value-viewer
                                 {:tag "object"
                                  :value (vector (symbol (pr-str (type ideref)))
                                                 #?(:clj (with-viewer `number-hex-viewer (System/identityHashCode ideref)))
                                                 (if-let [deref-as-map (resolve 'clojure.core/deref-as-map)]
                                                   (deref-as-map ideref)
                                                   (deref ideref)))})))})

(def regex-viewer
  {:name `regex-viewer
   :pred #?(:clj (partial instance? java.util.regex.Pattern) :cljs regexp?)
   :transform-fn (fn [wrapped-value] (with-viewer `tagged-value-viewer {:tag "" :value (let [regex (->value wrapped-value)]
                                                                                         #?(:clj (.pattern regex) :cljs (.-source regex)))}))})

(def fallback-viewer
  {:name `fallback-viewer :pred (constantly :true) :transform-fn (update-val #(with-viewer `read+inspect-viewer (pr-str %)))})

(def elision-viewer
  {:name `elision-viewer :render-fn 'nextjournal.clerk.render/render-elision :transform-fn mark-presented})

(def katex-viewer
  {:name `katex-viewer :render-fn 'nextjournal.clerk.render/render-katex :transform-fn mark-presented})

(def mathjax-viewer
  {:name `mathjax-viewer :render-fn 'nextjournal.clerk.render/render-mathjax :transform-fn mark-presented})

(defn transform-html [{:as wrapped-value :keys [path]}]
  (let [!path-idx (atom -1)]
    (update wrapped-value
            :nextjournal/value
            (fn [hiccup]
              (if (string? hiccup)
                [:div {:dangerouslySetInnerHTML {:__html hiccup}}]
                (w/postwalk (fn [x] (if (wrapped-value? x)
                                      [(inspect-fn)
                                       (present (inherit-opts wrapped-value x (swap! !path-idx inc)))]
                                      x))
                            hiccup))))))

(def html-viewer
  {:name `html-viewer
   :render-fn 'nextjournal.clerk.render/render-html
   :transform-fn (comp mark-presented transform-html)})

#_(present (with-viewer html-viewer [:div {:nextjournal/value (range 30)} {:nextjournal/value (range 30)}]))

(def plotly-viewer
  {:name `plotly-viewer :render-fn 'nextjournal.clerk.render/render-plotly :transform-fn mark-presented})

(def vega-lite-viewer
  {:name `vega-lite-viewer :render-fn 'nextjournal.clerk.render/render-vega-lite :transform-fn mark-presented})

(def markdown-viewer
  {:name `markdown-viewer
   :add-viewers markdown-viewers
   :transform-fn (fn [wrapped-value]
                   (-> wrapped-value
                       mark-presented
                       (update :nextjournal/value #(cond->> % (string? %) md/parse))
                       (with-md-viewer)))})

(def code-viewer
  {:name `code-viewer
   :render-fn 'nextjournal.clerk.render/render-code
   :transform-fn (comp mark-presented
                       #(update-in % [:nextjournal/render-opts :language] (fn [lang] (or lang "clojure")))
                       (update-val (fn [v] (if (string? v) v (str/trim (with-out-str (pprint/pprint v)))))))})

(def row-viewer
  {:name `row-viewer :render-fn '(fn [items opts]
                                   (let [item-count (count items)]
                                     (into [:div {:class "md:flex md:flex-row md:gap-4 not-prose"
                                                  :style opts}]
                                           (map (fn [item]
                                                  [:div.flex.items-center.justify-center.flex-auto
                                                   (nextjournal.clerk.render/inspect-presented opts item)])) items)))})

(def col-viewer
  {:name `col-viewer :render-fn '(fn [items opts]
                                   (into [:div {:class "md:flex md:flex-col md:gap-4 clerk-grid not-prose"
                                                :style opts}]
                                         (map (fn [item]
                                                [:div.flex.items-center.justify-center
                                                 (nextjournal.clerk.render/inspect-presented opts item)])) items))})

(def table-viewers
  [(-> string-viewer
       (dissoc :closing-paren :opening-paren)
       (assoc :render-fn 'nextjournal.clerk.render/render-string))
   (assoc number-viewer :render-fn 'nextjournal.clerk.render.table/render-table-number)
   (assoc elision-viewer :render-fn 'nextjournal.clerk.render.table/render-table-elision)
   table-missing-viewer
   table-markup-viewer
   table-head-viewer
   table-body-viewer
   table-row-viewer])

(declare html doc-url)

(def table-error-viewer
  {:name `table-error-viewer :render-fn 'nextjournal.clerk.render.table/render-table-error})

(def table-viewer
  {:name `table-viewer
   :add-viewers table-viewers
   :page-size 20
   :transform-fn (fn [{:as wrapped-value :nextjournal/keys [applied-viewer]}]
                   (if-let [{:keys [head rows]} (normalize-table-data (->value wrapped-value))]
                     (-> wrapped-value
                         (assoc :nextjournal/viewer `table-markup-viewer)
                         (update :nextjournal/width #(or % (when-not (empty? rows) :wide)))
                         (update :nextjournal/render-opts merge {:num-cols (count (or head (first rows)))
                                                                 :number-col? (into #{}
                                                                                    (comp (map-indexed vector)
                                                                                          (keep #(when (number? (second %)) (first %))))
                                                                                    (not-empty (first rows)))})
                         (assoc :nextjournal/value (cond->> [(with-viewer `table-body-viewer (merge (-> applied-viewer
                                                                                                        (select-keys [:page-size])
                                                                                                        (set/rename-keys {:page-size :nextjournal/page-size}))
                                                                                                    (select-keys wrapped-value [:nextjournal/page-size]))
                                                               (if (seq rows)
                                                                 (map (partial with-viewer `table-row-viewer) rows)
                                                                 [(html [:span.italic "this table has no rows"])]))]
                                                     head (cons (with-viewer (:name table-head-viewer table-head-viewer) head)))))
                     (-> wrapped-value
                         mark-presented
                         (assoc :nextjournal/width :wide)
                         (assoc :nextjournal/value [(present wrapped-value)])
                         (assoc :nextjournal/viewer (:name table-error-viewer)))))})

(def code-block-viewer
  {:name `code-block-viewer
   :transform-fn (update-val (some-fn :text-without-meta :text))
   :render-fn 'nextjournal.clerk.render/render-code-block})

(def folded-code-block-viewer
  {:name `folded-code-block-viewer
   :transform-fn (update-val (some-fn :text-without-meta :text))
   :render-fn 'nextjournal.clerk.render/render-folded-code-block})

(def tagged-value-viewer
  {:name `tagged-value-viewer
   :render-fn '(fn [{:keys [tag value space?]} opts]
                 (nextjournal.clerk.render/render-tagged-value
                  {:space? (:nextjournal/value space?)}
                  (str "#" (:nextjournal/value tag))
                  [nextjournal.clerk.render/inspect-presented value]))
   :transform-fn mark-preserve-keys})


#?(:cljs
   (def js-promise-viewer
     {:name `js-promise-viewer :pred #(instance? js/Promise %) :render-fn 'nextjournal.clerk.render/render-promise}))

#?(:cljs
   (def js-object-viewer
     {:name `js-object-viewer
      :pred goog/isObject
      :page-size 20
      :opening-paren "{" :closing-paren "}"
      :render-fn '(fn [v opts] (nextjournal.clerk.render/render-tagged-value {:space? true}
                                                                             "#js"
                                                                             (nextjournal.clerk.render/render-map v opts)))
      :transform-fn (update-val (fn [^js o]
                                  (into {}
                                        (comp (remove (fn [k] (identical? "function" (goog/typeOf (j/get o k)))))
                                              (map (fn [k]
                                                     [(symbol k)
                                                      (try (let [v (j/get o k)]
                                                             (.-constructor v) ;; test for SecurityError
                                                             ;; https://developer.mozilla.org/en-US/docs/Web/Security/Same-origin_policy
                                                             v)
                                                           (catch js/Error _ 'forbidden))]))) (js/Object.keys o))))}))

#?(:cljs
   (def js-array-viewer
     {:name `js-array-viewer
      :pred js-iterable?
      :transform-fn (update-val seq)
      :render-fn '(fn [v opts]
                    (nextjournal.clerk.render/render-tagged-value {:space? true}
                                                                  "#js"
                                                                  (nextjournal.clerk.render/render-coll v opts)))
      :opening-paren "[" :closing-paren "]"
      :page-size 20}))

(def result-viewer
  {:name `result-viewer
   :render-fn 'nextjournal.clerk.render/render-result
   :transform-fn transform-result})

#?(:clj
   (defn edn-roundtrippable? [x]
     (= x (-> x ->edn tools.reader/read-string))))

#?(:clj
   (defn throw-if-sync-var-is-invalid [var]
     (when-not (instance? IAtom @var)
       (throw (ex-info "Clerk cannot sync non-atom values. Vars meant for sync need to hold clojure atom values."
                       {:var var :value @var}
                       (IllegalArgumentException.))))
     (try (when-not (edn-roundtrippable? @@var)
            (throw (IllegalArgumentException.)))
          (catch Exception ex
            (throw (ex-info "Clerk can only sync values which can be round-tripped in EDN."
                            {:var var :value @var}
                            ex))))))

(defn extract-sync-atom-vars [{:as _doc :keys [blocks]}]
  (into #{}
        (keep (fn [{:keys [result form]}]
                (when-let [var (-> result :nextjournal/value (get-safe :nextjournal.clerk/var-from-def))]
                  (when (contains? (meta form) :nextjournal.clerk/sync)
                    #?(:clj (throw-if-sync-var-is-invalid var))
                    var))))
        blocks))

(defn atom-var-name->state [doc]
  (into {}
        (map (fn [sync-var]
               [(symbol sync-var) @@sync-var]))
        (extract-sync-atom-vars doc)))

(defn home? [{:keys [nav-path]}]
  (contains? #{"src/nextjournal/home.clj" "'nextjournal.clerk.home"} nav-path))

(defn route-index?
  "Should the index router be enabled?"
  [{:keys [expanded-paths]}]
  (boolean (seq expanded-paths)))


(defn index? [{:as opts :keys [file index ns]}]
  (or (= (some-> ns ns-name) 'nextjournal.clerk.index)
      (some->> file str (re-matches #"(^|.*/)(index\.(clj|cljc|md))$"))
      (and index (= file index))))

(defn index-path [{:as opts :keys [index]}]
  #?(:cljs ""
     :clj (if (route-index? opts)
            ""
            (if (fs/exists? "index.clj") "index.clj" "'nextjournal.clerk.index"))))

(defn header [{:as opts :keys [file-path nav-path package ns] :git/keys [url prefix sha]}]
  (html [:div.viewer.w-full.max-w-prose.px-8.not-prose.mt-3
         [:div.mb-8.text-xs.sans-serif.text-slate-400
          (when (and (not (route-index? opts))
                     (not (home? opts)))
            [:<>
             [:a.font-medium.border-b.border-dotted.border-slate-300.hover:text-indigo-500.hover:border-indigo-500.dark:border-slate-500.dark:hover:text-white.dark:hover:border-white.transition
              {:href (doc-url "'nextjournal.clerk.home")} "Home"]
             [:span.mx-2 "•"]])
          (when (not (index? opts))
            [:<>
             [:a.font-medium.border-b.border-dotted.border-slate-300.hover:text-indigo-500.hover:border-indigo-500.dark:border-slate-500.dark:hover:text-white.dark:hover:border-white.transition
              {:href (doc-url (index-path opts))} "Index"]
             [:span.mx-2 "•"]])
          [:span
           (if package "Generated with " "Served from ")
           [:a.font-medium.border-b.border-dotted.border-slate-300.hover:text-indigo-500.hover:border-indigo-500.dark:border-slate-500.dark:hover:text-white.dark:hover:border-white.transition
            {:href "https://clerk.vision"} "Clerk"]
           (let [default-index? (= 'nextjournal.clerk.index (some-> ns ns-name))]
             (when (or file-path default-index? url)
               [:<>
                " from "
                [:a.font-medium.border-b.border-dotted.border-slate-300.hover:text-indigo-500.hover:border-indigo-500.dark:border-slate-500.dark:hover:text-white.dark:hover:border-white.transition
                 {:href (when (and url sha) (if default-index? (str url "/tree/" sha) (str url "/blob/" sha "/" prefix file-path)))}
                 (if (and url default-index?) #?(:clj (subs (.getPath (URL. url)) 1) :cljs url) (or file-path nav-path))
                 (when sha [:<> "@" [:span.tabular-nums (subs sha 0 7)]])]]))]]]))

(def header-viewer
  {:name `header-viewer
   :transform-fn (comp mark-presented (update-val header))})

(defn md-toc->navbar-items [{:keys [children]}]
  (mapv (fn [{:as node :keys [emoji attrs]}]
          {:title (str/replace (md.transform/->text node) (re-pattern (str "^" emoji "[ ]?")) "")
           :emoji emoji
           :path (str "#" (:id attrs))
           :items (md-toc->navbar-items node)}) children))

(defn transform-toc [{:as wrapped-value doc :nextjournal/value}]
  (let [{:keys [toc-visibility package]} doc]
    (-> wrapped-value
        mark-presented
        (update :nextjournal/value (comp md-toc->navbar-items :toc))
        (update :nextjournal/render-opts assoc
                :toc-visibility toc-visibility :set-hash? (not= :single-file package)))))

(comment #?(:clj (nextjournal.clerk/recompute!)))

(def toc-viewer
  {:name `toc-viewer
   :transform-fn transform-toc
   :render-fn 'nextjournal.clerk.render.navbar/render-items})

(defn present-error [error]
  {:nextjournal/presented (present error)
   :nextjournal/blob-id (str (gensym "error"))})

(defn process-blocks [viewers {:as doc :keys [ns]}]
  (-> doc
      (assoc :atom-var-name->state (atom-var-name->state doc))
      (assoc :ns (->render-eval (list 'ns (if ns (ns-name ns) 'user))))
      (update :blocks (partial into [] (comp (mapcat (partial with-block-viewer (dissoc doc :error)))
                                             (map (comp present (partial ensure-wrapped-with-viewers viewers))))))
      (assoc :header (present (with-viewers viewers (with-viewer `header-viewer doc))))
      #_(assoc :footer (present (footer doc)))

      (assoc :toc (present (with-viewers viewers (with-viewer `toc-viewer doc))))
      (update :file str)

      (select-keys [:atom-var-name->state
                    :blocks :package
                    :doc-css-class
                    :error
                    :file
                    :open-graph
                    :ns
                    :title
                    :toc
                    :toc-visibility
                    :header
                    :footer])
      (update-if :error present-error)
      (assoc :sidenotes? (boolean (seq (:footnotes doc))))
      #?(:clj (cond-> ns (assoc :scope (datafy-scope ns))))))

(def notebook-viewer
  {:name `notebook-viewer
   :render-fn 'nextjournal.clerk.render/render-notebook
   :transform-fn (fn [{:as wrapped-value :nextjournal/keys [viewers]}]
                   (-> wrapped-value
                       (update :nextjournal/value (partial process-blocks viewers))
                       mark-presented))})

(def render-eval-viewer
  {:name `render-eval-viewer
   :pred render-eval?
   :var-from-def? true
   :transform-fn (comp mark-presented
                       (update-val
                        (fn [x]
                          (cond (render-eval? x) x
                                (seq? x) (->render-eval x)
                                (symbol? x) (->render-eval x)
                                (var? x) (->render-eval (list 'resolve (list 'quote (symbol x))))
                                (var-from-def? x) (recur (-> x :nextjournal.clerk/var-from-def symbol))))))
   :render-fn '(fn [x opts]
                 (if (nextjournal.clerk.render/reagent-atom? x)
                   ;; special atoms handling to support reactivity
                   [nextjournal.clerk.render/render-tagged-value {:space? false}
                    "#object"
                    [nextjournal.clerk.render/inspect [(symbol (pr-str (type x))) @x]]]
                   [nextjournal.clerk.render/inspect x]))})

(def ^{:deprecated "0.18"}
  viewer-eval-viewer
  "Use `render-eval-viewer` instead."
  render-eval-viewer)

(def default-viewers
  ;; maybe make this a sorted-map
  [header-viewer
   toc-viewer
   char-viewer
   string-viewer
   number-viewer
   number-hex-viewer
   symbol-viewer
   keyword-viewer
   nil-viewer
   boolean-viewer
   map-entry-viewer
   var-from-def-viewer
   read+inspect-viewer
   vector-viewer
   set-viewer
   sequential-viewer
   render-eval-viewer
   cell-viewer
   result-viewer
   map-viewer
   var-viewer
   throwable-viewer
   image-viewer
   ideref-viewer
   regex-viewer
   #?(:cljs js-promise-viewer)
   #?(:cljs js-array-viewer)
   #?(:cljs js-object-viewer)
   fallback-viewer
   elision-viewer
   katex-viewer
   mathjax-viewer
   html-viewer
   plotly-viewer
   vega-lite-viewer
   markdown-viewer
   row-viewer
   col-viewer
   table-viewer
   table-error-viewer
   code-viewer
   code-block-viewer
   folded-code-block-viewer
   tagged-value-viewer
   notebook-viewer
   hide-result-viewer])

(defonce
  ^{:doc "atom containing a map of and per-namespace viewers or `:defaults` overridden viewers."}
  !viewers
  (#?(:clj atom :cljs ratom/atom) {}))

#_(reset! !viewers {})

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
#_(find-viewer default-viewers (comp #{`elision-viewer} :name))

(defn find-named-viewer [viewers viewer-name]
  (find-viewer viewers (comp #{viewer-name} :name)))

#_(find-named-viewer default-viewers `elision-viewer)

(defn viewer-for [viewers x]
  (or (when-let [selected-viewer (->viewer x)]
        (if (or (symbol? selected-viewer)
                (keyword? selected-viewer))
          (or (find-named-viewer viewers selected-viewer)
              (throw (ex-info (str "cannot find viewer named " selected-viewer)
                              {:selected-viewer selected-viewer :viewers viewers})))
          selected-viewer))
      (find-viewer viewers (fn [{:keys [pred]}]
                             (and (ifn? pred) (if-let [wrapped-pred (and (map? pred)
                                                                         (ifn? (:wrapped pred))
                                                                         (:wrapped pred))]
                                                (wrapped-pred x)
                                                (pred (->value x))))))
      (throw (ex-info (str "cannot find matching viewer for value")
                      {:value (->value x) :viewers viewers :x x}))))

#_(viewer-for default-viewers [1 2 3])
#_(viewer-for default-viewers {:nextjournal/value [1 2 3]})
#_(viewer-for default-viewers 42)
#_(viewer-for default-viewers (with-viewer `html-viewer [:h1 "Hello Hiccup"]))
#_(viewer-for default-viewers (with-viewer {:transform-fn identity} [:h1 "Hello Hiccup"]))

(defn ensure-wrapped-with-viewers
  ([x] (ensure-wrapped-with-viewers (get-viewers (get-*ns*)) x))
  ([viewers x]
   (-> x
       ensure-wrapped
       (update :nextjournal/viewers (fn [x-viewers] (or x-viewers viewers))))))

#_(ensure-wrapped-with-viewers 42)
#_(ensure-wrapped-with-viewers {:nextjournal/value 42 :nextjournal/viewers [:boo]})

(defn hoist-nested-wrapped-value [x]
  (if (and (wrapped-value? x)
           (wrapped-value? (get-safe x :nextjournal/value)))
    (merge x (hoist-nested-wrapped-value (get-safe x :nextjournal/value)))
    x))

(defn apply-viewers* [wrapped-value]
  (let [hoisted-wrapped-value (hoist-nested-wrapped-value wrapped-value)
        viewers (->viewers hoisted-wrapped-value)
        _ (when (empty? viewers)
            (throw (ex-info "cannot apply empty viewers" {:wrapped-value wrapped-value})))
        {:as viewer viewers-to-add :add-viewers :keys [render-fn transform-fn]}
        (viewer-for viewers hoisted-wrapped-value)
        transformed-value (cond-> (ensure-wrapped-with-viewers viewers
                                                               (cond-> (-> hoisted-wrapped-value
                                                                           (dissoc :nextjournal/viewer)
                                                                           (assoc :nextjournal/applied-viewer viewer))
                                                                 transform-fn transform-fn))
                            viewers-to-add (update :nextjournal/viewers add-viewers viewers-to-add))
        wrapped-value' (cond-> transformed-value
                         (-> transformed-value ->value wrapped-value?)
                         (merge (->value transformed-value)))]
    (if (and transform-fn (not render-fn))
      (recur wrapped-value')
      (-> wrapped-value'
          (assoc :nextjournal/viewer viewer)
          (merge (->opts wrapped-value))))))

(defn apply-viewers [x]
  (apply-viewers* (ensure-wrapped-with-viewers x)))

#_(= (apply-viewers {:nextjournal/value (with-viewer number-viewer 123)})
     (apply-viewers {:nextjournal/value {:nextjournal/value (with-viewer number-viewer 123)}}))

#_(apply-viewers 42)
#_(apply-viewers {:one :two})
#_(apply-viewers {:one :two})
#_(apply-viewers [1 2 3])
#_(apply-viewers (range 3))
#_(apply-viewers (clojure.java.io/file "notebooks"))
#_(apply-viewers (md "# Hello"))
#_(apply-viewers (html [:h1 "hi"]))
#_(apply-viewers (with-viewer `elision-viewer {:remaining 10 :count 30 :offset 19}))
#_(apply-viewers (with-viewer (->Form '(fn [name] (html [:<> "Hello " name]))) "James"))
#_(apply-viewers (with-viewers [{:pred (constantly true) :render-fn '(fn [x] [:h1 "hi"])}] 42))

(defn count-viewers
  "Helper function to walk a given `x` and replace the viewers with their counts. Useful for debugging."
  [x]
  (w/postwalk #(if (wrapped-value? %)
                 (cond-> (dissoc % :!budget)
                   (:nextjournal/viewers %)
                   (-> #_%
                       (update :nextjournal/viewers count)
                       (set/rename-keys {:nextjournal/viewers :nextjournal/viewers-count})))
                 %) x))

(defn bounded-count-opts [n xs]
  (when-not (number? n)
    (throw (ex-info "n must be a number?" {:n n :xs xs})))
  (let [limit (+ n #?(:clj config/*bounded-count-limit* :cljs 10000))
        total (try (bounded-count limit xs)
                   (catch #?(:clj Exception :cljs js/Error) _
                     nil))]
    (cond-> {}
      total (assoc :total total)
      (or (not total) (= total limit)) (assoc :unbounded? true))))

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

(defn process-render-fn [{:as viewer :keys [render-fn render-evaluator]}]
  (cond-> viewer
    (and render-fn (not (render-fn? render-fn)))
    (update :render-fn (fn [rf]
                         (assoc (->render-fn rf)
                                :render-evaluator (or render-evaluator :sci))))))

(defn hash-sha1 [x]
  #?(:clj (analyzer/valuehash :sha1 x)
     :cljs (let [hasher (goog.crypt.Sha1.)]
             (.update hasher (goog.crypt/stringToUtf8ByteArray (pr-str x)))
             (.digest hasher))))

(defn validate-viewer! [{:as viewer :keys [render-fn]}]
  #?(:clj (when (and render-fn (not (or (seq? render-fn)
                                        (symbol? render-fn))))
            (throw (ex-info (str "`:render-fn` must to be a quoted form or symbol, got a "
                                 (if (fn? render-fn) "function" (type render-fn))
                                 " instead.")
                            {:viewer viewer
                             :render-fn-type (type render-fn)})))))

(defn process-viewer [viewer {:nextjournal/keys [render-evaluator]}]
  ;; TODO: drop wrapped-value arg here and handle this elsewhere by
  ;; passing modified viewer stack
  ;; `(clerk/update-viewers viewers {:render-fn #(assoc % :render-evaluator :cherry)})`
  (if-not (map? viewer)
    viewer
    (-> (doto viewer
          validate-viewer!)
        (cond-> (and (not (:render-evaluator viewer)) render-evaluator)
          (assoc :render-evaluator render-evaluator))
        (dissoc :add-viewers :pred :transform-fn :update-viewers-fn)
        (as-> viewer (assoc viewer :hash (hash-sha1 viewer)))
        (process-render-fn))))

#_(process-viewer {:render-fn '#(vector :h1) :transform-fn mark-presented})

(def processed-keys
  (into [:path :offset :n :nextjournal/content-type :nextjournal/value]
        (-> viewer-opts-normalization vals set (disj :nextjournal/viewers))))

(defn process-wrapped-value [{:as wrapped-value :keys [present-elision-fn path]}]
  (cond-> (-> wrapped-value
              (select-keys processed-keys)
              (dissoc :nextjournal/budget)
              (update :nextjournal/viewer process-viewer wrapped-value))
    present-elision-fn (vary-meta assoc :present-elision-fn present-elision-fn)))

#_(process-wrapped-value (apply-viewers 42))

(defn make-elision [viewers fetch-opts]
  (->> (with-viewer `elision-viewer fetch-opts)
       (ensure-wrapped-with-viewers viewers)
       apply-viewers
       process-wrapped-value))

#_(present (make-elision default-viewers {:n 20}))

(defn find-elision [desc]
  (->value (first (filter (comp #{`elision-viewer} :name :nextjournal/viewer)
                          (tree-seq (some-fn map? vector?) #(cond-> % (map? %) vals) desc)))))

(defn ->fetch-opts [wrapped-value]
  (merge {:n (:nextjournal/page-size wrapped-value (-> wrapped-value ->viewer :page-size))}
         (select-keys wrapped-value [:path :offset])))

(defn get-elision [wrapped-value]
  (let [{:as fetch-opts :keys [n]} (->fetch-opts wrapped-value)]
    (when (number? n)
      (merge fetch-opts (bounded-count-opts n (->value wrapped-value))))))

#_(get-elision (present (range)))
#_(get-elision (present "abc"))
#_(get-elision (present (str/join (repeat 1000 "abc"))))

(defn present+paginate-children [{:as wrapped-value :nextjournal/keys [budget viewers preserve-keys-fn] :keys [!budget]}]
  (let [{:as fetch-opts :keys [offset n]} (->fetch-opts wrapped-value)
        xs (->value wrapped-value)
        paginate? (and (number? n) (not preserve-keys-fn))
        fetch-opts' (cond-> fetch-opts
                      (and paginate? !budget (not (map-entry? xs)))
                      (update :n min @!budget))
        children (if preserve-keys-fn
                   (into {}
                         (map (fn [[k v]]
                                [k (if (preserve-keys-fn k)
                                     v
                                     (present* (inherit-opts wrapped-value v k)))]))
                         xs)
                   (into []
                         (comp (if paginate? (drop+take-xf fetch-opts') identity)
                               (map-indexed (fn [i x] (present* (inherit-opts wrapped-value x (+ i (or offset 0))))))
                               (remove nil?))
                         (ensure-sorted xs)))
        {:as elision :keys [total unbounded?]} (and paginate? (get-elision wrapped-value))
        new-offset (when paginate? (or (some-> children peek :path peek inc) 0))]
    (cond-> children
      (and paginate? (or unbounded? (< new-offset total)))
      (conj (let [fetch-opts (assoc elision :offset new-offset)]
              (make-elision viewers fetch-opts))))))

(defn present+paginate-string [{:as wrapped-value :nextjournal/keys [viewers value]}]
  (let [{:as elision :keys [n total path offset]} (get-elision wrapped-value)]
    (if (and elision n (< n total))
      (let [new-offset (min (+ (or offset 0) n) total)]
        (cond-> [(subs value (or offset 0) new-offset)]
          (pos? (- total new-offset)) (conj (let [fetch-opts (-> elision
                                                                 (assoc :offset new-offset :replace-path (conj path new-offset)))]
                                              (make-elision viewers fetch-opts)))
          true ensure-wrapped))
      value)))

(defn ->budget [opts]
  (:nextjournal/budget opts 200))

(defn make-!budget-opts [opts]
  (let [budget (->budget opts)]
    (cond-> {:nextjournal/budget budget}
      budget (assoc :!budget (atom budget)))))

#_(make-!budget-opts {})
#_(make-!budget-opts {:nextjournal/budget 42})
#_(make-!budget-opts {:nextjournal/budget nil})
#_(make-!budget-opts (make-!budget-opts {:nextjournal/budget nil}))

(defn ^:private present-elision* [!path->wrapped-value {:as fetch-opts :keys [path]}]
  (if-let [wrapped-value (@!path->wrapped-value path)]
    (present* (merge wrapped-value (make-!budget-opts wrapped-value) fetch-opts))
    (throw (ex-info "could not find wrapped-value at path" {:!path->wrapped-value !path->wrapped-value :fetch-otps fetch-opts}))))


(defn ^:private present* [{:as wrapped-value
                           :keys [path !budget store!-wrapped-value]
                           :nextjournal/keys [viewers]}]
  (when (empty? viewers)
    (throw (ex-info "cannot present* with empty viewers" {:wrapped-value wrapped-value})))
  (when store!-wrapped-value
    (store!-wrapped-value wrapped-value))
  (let [{:as wrapped-value-applied :nextjournal/keys [presented?]} (apply-viewers* wrapped-value)
        xs (->value wrapped-value-applied)]
    #_(prn :xs xs :type (type xs) :path path)
    (when (and !budget (not presented?))
      (swap! !budget #(max (dec %) 0)))
    (-> (merge (->opts wrapped-value-applied)
               (when (empty? path) (select-keys wrapped-value [:present-elision-fn]))
               (with-viewer (->viewer wrapped-value-applied)
                 (cond presented?
                       wrapped-value-applied

                       (string? xs)
                       (present+paginate-string wrapped-value-applied)

                       (and xs (seqable? xs))
                       (present+paginate-children wrapped-value-applied)

                       :else ;; leaf value
                       xs)))
        process-wrapped-value)))

(defn assign-content-lengths [wrapped-value]
  (w/postwalk
   (fn [x]
     (if-let [value (and (wrapped-value? x) (:nextjournal/value x))]
       (let [{:nextjournal/keys [viewer]} x
             {:keys [name opening-paren closing-paren]} viewer
             elision-content-length 6]
         (assoc x
                :content-length
                (cond
                  (or (nil? value) (char? value) (string? value) (keyword? value) (symbol? value) (number? value))
                  (count (pr-str value))
                  (contains? #{`elision-viewer} name)
                  elision-content-length
                  (contains? #{`map-entry-viewer} name)
                  (reduce + 1 (map #(:content-length % 0) value))
                  (vector? value)
                  (->> value
                       (map #(:content-length % 0))
                       (reduce + (+ (count opening-paren) (count closing-paren)))
                       (+ (dec (count value))))
                  :else 0)
                :type name))
       x))
   wrapped-value))

(defn compute-expanded-at [{:as state :keys [indents expanded-at prev-type]}
                           {:nextjournal/keys [value]
                            :keys [content-length path type]
                            :or {content-length 0}}]
  (let [max-length (- 80 (reduce + 0 indents))
        expanded? (< max-length content-length)
        state' (assoc state
                      :expanded-at (assoc expanded-at path expanded?)
                      :prev-type type
                      :indents (conj
                                (->> indents (take (count path)) vec)
                                (cond
                                  (contains? #{:map-entry} prev-type) (or content-length 0)
                                  (vector? value) 2
                                  :else 1)))]
    (reduce compute-expanded-at state' (cond
                                         (vector? value) value
                                         (map? value) (vals value)))))


(defn collect-expandable-paths [state wrapped-value]
  (if-let [{:nextjournal/keys [value] :keys [path]} (when (wrapped-value? wrapped-value)
                                                      wrapped-value)]
    (reduce collect-expandable-paths
            (cond-> state path (assoc-in [:expanded-at path] false))
            (when (vector? value) value))
    state))

(defn assign-expanded-at [{:as wrapped-value :keys [content-length]}]
  (assoc wrapped-value :nextjournal/expanded-at (:expanded-at (if content-length
                                                                (compute-expanded-at {:expanded-at {}} wrapped-value)
                                                                (collect-expandable-paths {:expanded-at {}} wrapped-value)))))

(comment
  (:nextjournal/expanded-at (present {:a-vector [1 2 3] :a-list '(123 234 345) :a-set #{1 2 3 4}}))
  (= (count "[1 2 [1 [2] 3] 4 5]")
     (:content-length (assign-content-lengths (present [1 2 [1 [2] 3] 4 5]))))
  (= (count "{:a-vector [1 2 3] :a-list (123 234 345) :a-set #{1 2 3 4}}")
     (:content-length (assign-content-lengths (present {:a-vector [1 2 3] :a-list '(123 234 345) :a-set #{1 2 3 4}}))))
  ;; Check for elisions as well
  (assign-content-lengths (present {:foo (vec (repeat 2 {:baz (range 30) :fooze (range 40)})) :bar (range 20)})))


(defn present
  "Presents the given value `x`.

  Transparently handles wrapped values and supports customization this way."
  [x]
  (let [opts (when (wrapped-value? x)
               (->opts (normalize-viewer-opts x)))
        !path->wrapped-value (atom {})]
    (-> (ensure-wrapped-with-viewers x)
        (merge {:store!-wrapped-value (fn [{:as wrapped-value :keys [path]}]
                                        (swap! !path->wrapped-value assoc path wrapped-value))
                :present-elision-fn (partial present-elision* !path->wrapped-value)
                :path (:path opts [])}
               (make-!budget-opts opts)
               opts)
        present*
        assign-closing-parens)))

(comment
  (present [\a \b])
  (present [42])
  (-> (present (range 100)) ->value peek)
  (present {:hello [1 2 3]})
  (present {:one [1 2 3] 1 2 3 4})
  (present [1 2 [1 [2] 3] 4 5])
  (present (clojure.java.io/file "notebooks"))
  (present {:nextjournal/viewers [{:pred sequential? :render-fn pr-str}] :nextjournal/value (range 100)})
  (present (map vector (range)))
  (present (subs (slurp "/usr/share/dict/words") 0 1000))
  (present (plotly {:data [{:z [[1 2 3] [3 2 1]] :type "surface"}]}))
  (present [(with-viewer `html-viewer [:h1 "hi"])])
  (present (with-viewer `html-viewer [:ul (for [x (range 3)] [:li x])]))
  (present (range))
  (present {1 [2]})
  (present (with-viewer '(fn [name] (html [:<> "Hello " name])) "James"))
  (present {:foo (vec (repeat 2 {:baz (range 30) :fooze (range 40)})) :bar (range 20)}))

(defn desc->values
  "Takes a `description` and returns its value. Inverse of `present`. Mostly useful for debugging."
  [desc]
  (let [x (->value desc)
        viewer-name (-> desc ->viewer :name)]
    (cond (= viewer-name `elision-viewer) (with-meta '... x)
          (= viewer-name `html-viewer) (update desc :nextjournal/value desc->values)
          (and (vector? x) (= (first x) (inspect-fn))) {:nextjournal/value (desc->values (second x))}
          (coll? x) (into (case viewer-name
                            (nextjournal.clerk.viewer/map-viewer
                             nextjournal.clerk.viewer/table-viewer) {}
                            (or (empty x) []))
                          (map desc->values)
                          x)
          :else x)))

#_(desc->values (present [1 [2 {:a :b} 2] 3 (range 100)]))
#_(desc->values (present (table (mapv vector (range 30)))))
#_(desc->values (present (with-viewer `table-viewer (normalize-table-data (repeat 60 ["Adelie" "Biscoe" 50 30 200 5000 :female])))))

(defn merge-presentations [root more elision]
  (w/postwalk
   (fn [x] (if (some #(= elision (:nextjournal/value %)) (when (coll? x) x))
             (into (pop x) (:nextjournal/value more))
             x))
   root))

(defn assign-closing-parens
  ([node] (assign-closing-parens '() node))
  ([closing-parens node]
   (let [value (->value node)
         viewer (->viewer node)
         closing (:closing-paren viewer)
         non-leaf? (and (vector? value) (wrapped-value? (first value)))
         defer-closing? (and non-leaf?
                             (or (-> value last :nextjournal/viewer :closing-paren) ;; the last element can carry parens
                                 (and (= `map-entry-viewer (-> value last :nextjournal/viewer :name)) ;; the last element is a map entry whose value can carry parens
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

(defn reset-viewers!
  ([viewers] (reset-viewers! (get-*ns*) viewers))
  ([scope viewers]
   (swap! !viewers assoc (datafy-scope scope) viewers)
   viewers))

(defn add-viewers! [viewers]
  (reset-viewers! (get-*ns*) (add-viewers (get-default-viewers) viewers)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; public convenience api
(def html         (partial with-viewer (:name html-viewer)))
(def md           (partial with-viewer (:name markdown-viewer)))
(def plotly       (partial with-viewer (:name plotly-viewer)))
(def vl           (partial with-viewer (:name vega-lite-viewer)))
(def table        (partial with-viewer (:name table-viewer)))
(def row          (partial with-viewer-extracting-opts (:name row-viewer)))
(def col          (partial with-viewer-extracting-opts (:name col-viewer)))
(def tex          (partial with-viewer (:name katex-viewer)))
(def notebook     (partial with-viewer (:name notebook-viewer)))
(def code         (partial with-viewer (:name code-viewer)))

(defn image
  ([image-or-url] (image {} image-or-url))
  ([viewer-opts image-or-url]
   (with-viewer (:name image-viewer) viewer-opts
     #?(:cljs image-or-url :clj (read-image image-or-url)))))

(defn caption [text content]
  (col
   content
   (html [:figcaption.text-center.mt-1 (md text)])))

(defn ^:dynamic doc-url
  ([path] (doc-url path nil))
  ([path fragment]
   (str "/" path (when fragment (str "#" fragment)))))

#_(doc-url "notebooks/rule_30.clj#board")
#_(doc-url "notebooks/rule_30.clj")

(defn print-hide-result-deprecation-warning []
  #?(:clj (binding [*out* *err*]
            (prn "`hide-result` has been deprecated, please put `^{:nextjournal.clerk/visibility {:result :hide}}` metadata on the form instead."))))

(defn hide-result
  "Deprecated, please put `^{:nextjournal.clerk/visibility {:result :hide}}` metadata on the form instead."
  {:deprecated "0.10"}
  ([x] (print-hide-result-deprecation-warning) (with-viewer hide-result-viewer {} x))
  ([viewer-opts x] (print-hide-result-deprecation-warning) (with-viewer hide-result-viewer viewer-opts x)))

(defn ^:private rewrite-for-cherry
  "Takes a form as generated by `eval-cljs` or `eval-cljs-str` and rewrites it for cherry compatibility meaning:
  * rewriting `nextjournal.clerk.sci-env/load-string+`"
  [form]
  (if (and (seq? form)
           (= 'nextjournal.clerk.sci-env/load-string+ (first form)))
    (list 'js/global_eval (list 'nextjournal.clerk.cherry-env/cherry-compile-string (second form)))
    form))

#_(rewrite-for-cherry '(binding [*ns* *ns*] (prn :foo)))
#_(rewrite-for-cherry '(binding [*ns* *ns*] (load-string "(prn :foo)")))
#_(rewrite-for-cherry '(nextjournal.clerk.sci-env/load-string+ "(this-as foo)"))

(defn ^:private maybe-rewrite-cljs-form-for-cherry [{:as wrapped-value :nextjournal/keys [render-evaluator]}]
  (cond-> wrapped-value
    (= :cherry render-evaluator)
    (update :nextjournal/value
            (fn [{:as render-eval :keys [form]}]
              (-> render-eval
                  (assoc :render-evaluator render-evaluator)
                  (update :form rewrite-for-cherry))))))

(defn eval-cljs
  ([form] (eval-cljs {} form))
  ([viewer-opts form]
   (with-viewer (-> render-eval-viewer
                    (update :transform-fn comp maybe-rewrite-cljs-form-for-cherry)
                    (assoc :nextjournal.clerk/remount (hash-sha1 form)))
     viewer-opts
     (->render-eval form))))

(defn eval-cljs-str
  ([code-string] (eval-cljs-str {} code-string))
  ([opts code-string]
   ;; NOTE: this relies on implementation details on how SCI code is evaluated
   ;; and will change in a future version of Clerk
   (eval-cljs opts (list 'nextjournal.clerk.sci-env/load-string+ code-string))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; examples
(def example-viewer
  {:name `example-viewer
   :transform-fn (fn [wrapped-value]
                   (-> wrapped-value
                       mark-preserve-keys
                       (assoc :nextjournal/viewer {:render-fn '(fn [{:keys [form val]} opts]
                                                                 [:div.mb-3.last:mb-0
                                                                  [:div.bg-slate-100.dark:bg-slate-800.px-4.py-2.border-l-2.border-slate-200.dark:border-slate-700
                                                                   (nextjournal.clerk.render/inspect-presented opts form)]
                                                                  [:div.pt-2.px-4.border-l-2.border-transparent
                                                                   (nextjournal.clerk.render/inspect-presented opts val)]])})
                       (update-in [:nextjournal/value :val] maybe-wrap-var-from-def (get-in wrapped-value [:nextjournal/value :form]))
                       (update-in [:nextjournal/value :form] code)))})

(def examples-viewer
  {:name `examples-viewer
   :transform-fn (update-val (fn [examples]
                               (mapv (partial with-viewer example-viewer) examples)))
   :render-fn '(fn [examples opts]
                 [:div
                  [:div.uppercase.tracking-wider.text-xs.font-sans.font-bold.text-slate-500.dark:text-white.mb-2.mt-3 "Examples"]
                  (into [:div]
                        (nextjournal.clerk.render/inspect-children opts) examples)])})
