(ns nextjournal.clerk.parser
  "Clerk's Parser turns Clojure & Markdown files and strings into Clerk documents."
  (:refer-clojure :exclude [read-string])
  (:require #?@(:bb [[clojure.tools.reader :as tools.reader]
                     [multiformats.hash :as hash]]
                :clj [[clojure.tools.reader :as tools.reader]
                      [taoensso.nippy :as nippy]
                      [multiformats.hash :as hash]]
                :cljs [[goog.crypt]
                       [goog.crypt.Sha1]])
            [clojure.set :as set]
            [clojure.string :as str]
            [clojure.zip]
            [edamame.core :as edamame]
            #?(:clj [nextjournal.clerk.utils :refer [->base58]])
            [nextjournal.markdown :as markdown]
            [rewrite-clj.node :as n]
            [rewrite-clj.parser :as p]
            [rewrite-clj.zip :as z]))



#?(:clj
   (defn auto-resolves [ns]
     (let [aliases (ns-aliases ns)
           aliases (assoc aliases :current (ns-name *ns*))]
       (zipmap (keys aliases)
               (map ns-name (vals aliases))))))

#_(auto-resolves (find-ns 'nextjournal.clerk.parser))
#_(auto-resolves (find-ns 'cards))


(defn read-string [s]
  (edamame/parse-string s {:all true
                           :read-cond :allow
                           :regex #(list `re-pattern %)
                           :features #?(:bb #{:bb :clj}
                                        :default #{:clj})
                           :end-location false
                           :row-key :line
                           :col-key :column
                           #?@(:clj [:readers *data-readers*
                                     :syntax-quote {:resolve-symbol tools.reader/resolve-symbol}
                                     :auto-resolve (auto-resolves (or *ns* (find-ns 'user)))])}))

#_(read-string "(ns rule-30 (:require [nextjournal.clerk.viewer :as v]))")


(defn ns? [form]
  (and (seq? form) (= 'ns (first form))))

(defn remove-leading-semicolons [s]
  (str/replace s #"^[;]+" ""))


(defn ^:private legacy-doc-visibility [form]
  (when-let [visibility (-> form meta :nextjournal.clerk/visibility)]
    (when-let [visibility-set (cond
                                (keyword? visibility) #{visibility}
                                (set? visibility) visibility)]
      {:code (or (some #(get visibility-set %) [:hide :fold]) :show)})))

#_(legacy-doc-visibility '^{:nextjournal.clerk/visibility :hide-ns} (ns foo))
#_(legacy-doc-visibility '^{:nextjournal.clerk/visibility :fold} (ns foo))
#_(legacy-doc-visibility '^{:nextjournal.clerk/visibility :hide} (ns foo))

(defn ^:private legacy-form-visibility [form visibility]
  (when-let [legacy-visibility (cond
                                 (keyword? visibility) #{visibility}
                                 (set? visibility) visibility)]
    (let [visibility-set' (cond-> legacy-visibility
                            (:hide-ns legacy-visibility) (conj legacy-visibility :hide))]
      (merge {:code (or (some #(get visibility-set' %) [:hide :fold]) :show)}
             (when (or (some-> form meta :nextjournal.clerk/viewer name (= "hide-result"))
                       (and (seq? form) (symbol? (first form)) (= "hide-result" (name (first form)))))
               {:result :hide})))))

#_(legacy-form-visibility '^{:nextjournal.clerk/visibility :hide-ns} (ns foo) :hide-ns)
#_(legacy-form-visibility '^{:nextjournal.clerk/visibility :fold} (ns foo) :fold)
#_(legacy-form-visibility '^{:nextjournal.clerk/visibility :hide} (ns foo) :hide)
#_(legacy-form-visibility '^{:nextjournal.clerk/visibility :show :nextjournal.clerk/viewer :hide-result} (def my-range (range 600)) :show)
#_(legacy-form-visibility '^{:nextjournal.clerk/visibility :show :nextjournal.clerk/viewer nextjournal.clerk/hide-result} (def my-range (range 500)) :show)

(defn visibility-marker? [form]
  (and (map? form) (contains? form :nextjournal.clerk/visibility)))

(def block-settings
  #{:nextjournal.clerk/auto-expand-results?
    :nextjournal.clerk/budget
    :nextjournal.clerk/css-class
    :nextjournal.clerk/no-cache
    :nextjournal.clerk/render-opts
    :nextjournal.clerk/page-size
    :nextjournal.clerk/render-evaluator
    :nextjournal.clerk/visibility
    :nextjournal.clerk/width})

(defn settings-marker? [form]
  (boolean (and (map? form)
                (some (fn [setting] (contains? form setting)) block-settings))))

#_(settings-marker? {:foo :bar})
#_(settings-marker? {:nextjournal.clerk/budget nil})

(defn parse-visibility [form visibility]
  (or (legacy-form-visibility form visibility) ;; TODO: drop legacy visibiliy support before 1.0
      (when-let [visibility-map (and visibility (cond->> visibility (not (map? visibility)) (hash-map :code)))]
        (when-not (and (every? #{:code :result} (keys visibility-map))
                       (every? #{:hide :show :fold} (vals visibility-map)))
          (throw (ex-info "Invalid `:nextjournal.clerk/visibility`, please pass a map with `:code` and `:result` keys, allowed values are `:hide`, `:show` and `:fold`."
                          (cond-> {:visibility visibility}
                            form (assoc :form form)))))
        visibility-map)))

#_(parse-visibility nil nil)
#_(parse-visibility nil {:code :fold :result :hide})

(defn ->visibility [form]
  (if (settings-marker? form)
    {:code :hide :result :hide}
    (cond-> (parse-visibility form (-> form meta :nextjournal.clerk/visibility))
      (ns? form) (merge {:result :hide}))))

#_(->visibility (quote ^{:nextjournal.clerk/visibility :fold} (ns foo)))
#_(->visibility '(foo :bar))
#_(->visibility (quote (ns foo {:nextjournal.clerk/visibility {:code :fold :result :hide}})))
#_(->visibility (quote ^{:nextjournal.clerk/visibility {:code :fold :result :hide}} (ns foo)))
#_(->visibility (quote ^{:nextjournal.clerk/visibility :hidden} (ns foo)))
#_(->visibility (quote ^{:nextjournal.clerk/visibility "bam"} (ns foo)))
#_(->visibility (quote ^{:nextjournal.clerk/visibility #{:hide-ns}} (do :foo)))

(defn get-doc-setting [form key]
  (or (when (ns? form) (or (some key form)
                           (-> form second meta key)))
      (when (map? form) (get form key))))

(defn ->doc-visibility [form]
  (cond
    ;; TODO: drop legacy visibility support before 1.0
    (and (ns? form) (legacy-doc-visibility form))
    (legacy-doc-visibility form)

    (or (ns? form) (visibility-marker? form))
    (parse-visibility form (get-doc-setting form :nextjournal.clerk/visibility))))

#_(->doc-visibility '(ns foo "my docs" {:nextjournal.clerk/visibility {:code :fold :result :hide}}))
#_(->doc-visibility '{:nextjournal.clerk/visibility {:code :fold}})
#_(->doc-visibility '(ns foo "my docs" {}))
#_(->doc-visibility '(ns ^{:nextjournal.clerk/visibility {:code :fold :result :hide}} foo))
#_(->doc-visibility '(ns ^{:nextjournal.clerk/visibility {:code :fold}} foo
                       {:nextjournal.clerk/visibility {:result :hide}}))

(defn parse-error-on-missing-vars [first-form]
  (if-some [setting (get-doc-setting first-form :nextjournal.clerk/error-on-missing-vars)]
    (do (when-not (#{:on :off} setting)
          (throw (ex-info (str "Invalid setting `" (pr-str setting) "` for `:nextjournal.clerk/error-on-missing-vars`. Valid values are `:on` and `:off`.")
                          {:nextjournal.clerk/error-on-missing-vars setting})))
        setting)
    (if (ns? first-form) :on :off)))

(defn ->open-graph [{:keys [title blocks]}]
  (merge (let [desc (first (sequence
                            (comp (keep :doc)
                                  (mapcat :content)
                                  (filter (comp #{:paragraph} :type))
                                  (map markdown/node->text))
                            blocks))]
           (cond-> {:type "article:clerk"}
             title (assoc :title title)
             desc (assoc :description desc)))
         (some #(get-doc-setting (:form %) :nextjournal.clerk/open-graph) blocks)))

#_(->open-graph
   (nextjournal.clerk.analyzer/analyze-doc
    (parse-file "notebooks/open_graph.clj")))

(defn add-open-graph-metadata [doc]
  (assoc doc :open-graph (->open-graph doc)))

(defn parse-global-block-settings
  "Parses the global (doc-wide) settings the given `form` if this node
  is `ns?` or a `settings-marker?`. Returns `nil` otherwise."
  [form]
  (when (or (ns? form) (settings-marker? form))
    (merge (when-let [val (->doc-visibility form)]
             {:nextjournal.clerk/visibility val})
           (select-keys (cond (settings-marker? form) form
                              (ns? form) (first (filter map? form)))
                        (disj block-settings :nextjournal.clerk/visibility)))))

#_(parse-global-block-settings '(ns foo {:nextjournal.clerk/visibility {:code :fold}}))
#_(parse-global-block-settings '(ns foo {:nextjournal.clerk/budget nil :nextjournal.clerk/width :full}))

(defn parse-local-block-settings
  "Parses the local settings of the given `form`."
  [form]
  (merge (some->> (->visibility form) (hash-map :nextjournal.clerk/visibility))
         (select-keys (merge (meta form)
                             (cond (settings-marker? form) form
                                   (ns? form) (first (filter map? form))))
                      (disj block-settings :nextjournal.clerk/visibility))))

#_(parse-local-block-settings '(ns foo))
#_(parse-local-block-settings '^{:nextjournal.clerk/budget nil
                                 :nextjournal.clerk/visibility {:code :fold}}(inc 1))


(defn ->doc-settings [first-form]
  (let [doc-css-class (when (ns? first-form)
                        (:nextjournal.clerk/doc-css-class (first (filter map? first-form))))]
    (cond-> {:ns? (ns? first-form)
             :error-on-missing-vars (parse-error-on-missing-vars first-form)
             :toc-visibility (or (#{true :collapsed} (:nextjournal.clerk/toc
                                                      (merge (-> first-form meta) ;; TODO: deprecate
                                                             (when (ns? first-form)
                                                               (merge (-> first-form second meta)
                                                                      (first (filter map? first-form)))))))
                                 false)
             :no-cache (boolean
                        (:nextjournal.clerk/no-cache
                         (or (when (map? first-form)
                               first-form)
                             (when (ns? first-form)
                               (first (filter map? first-form))))))}
      doc-css-class (assoc :doc-css-class (cond-> doc-css-class
                                            (or (keyword? doc-css-class) (string? doc-css-class))
                                            vector) ))))


#_(->doc-settings '(ns foo {:nextjournal.clerk/visbility {:code :fold}}))
#_(->doc-settings '(ns foo {:nextjournal.clerk/budget nil :nextjournal.clerk/width :full}))
#_(->doc-settings '^{:nextjournal.clerk/toc :boom} (ns foo)) ;; TODO: error

(defn deflike? [form]
  (and (seq? form) (symbol? (first form)) (str/starts-with? (name (first form)) "def")))

#_(deflike? '(defonce foo :bar))
#_(deflike? '(rdef foo :bar))

(defn markdown? [{:as block :keys [type]}]
  (contains? #{:markdown} type))

(defn code? [{:as block :keys [type]}]
  (contains? #{:code} type))

(defn merge-settings [current-settings new-settings]
  (-> (merge-with merge current-settings (select-keys new-settings [:nextjournal.clerk/visibility]))
      (merge (dissoc new-settings :nextjournal.clerk/visibility))))

#_(merge-settings {:nextjournal.clerk/visibility {:code :show :result :show}} {:nextjournal.clerk/visibility {:code :fold}})

(def code-tags
  #{:deref :map :meta :multi-line :list :quote :syntax-quote :reader-macro :set :token :var :vector})

(def whitespace-on-line-tags
  #{:comment :whitespace :comma})

(defn deflike-node? [node]
  (and (= :list (n/tag node))
       (when-some [first-child (some-> node n/children first)]
         (and (n/sexpr-able? first-child)
              (when-some [s (n/sexpr first-child)]
                (and (symbol? s) (nil? (namespace s)) (str/starts-with? (name s) "def")))))))

(def clerk-namespace? (comp #{"nextjournal.clerk"} namespace))

(defn pop-children
  "Returns a new location with the first child (and all whitespace following it) removed, without moving. Child nodes are wrapped in a `:forms` node."
  [zloc]
  ;; rewreite-clj doesn't allow to z/remove the left component of a meta node
  (z/replace zloc (n/forms-node (drop-while n/whitespace? (clojure.zip/rights (z/down zloc))))))

(defn root-location [zloc] (last (take-while some? (iterate z/up zloc))))

(defn remove-clerk-keys
  "Takes a map zipper location, returns a new location representing the input map node with all `::clerk` namespaced keys removed.
   Whitespace is preserved when possible."
  [map-loc]
  (loop [loc (z/down map-loc) parent map-loc]
    (if-not loc
      parent
      (let [s (-> loc z/sexpr)]
        (if (and (keyword? s) (clerk-namespace? s))
          (let [updated (-> loc z/right z/remove z/remove)]
            (recur (z/next updated) (root-location updated)))
          (recur (-> loc z/right z/right) parent))))))

(defn zip->node-with-clerk-metadata-removed [zloc]
  (loop [z zloc]
    (cond
      (= :meta (z/tag z))
      (let [meta-loc (z/down z)
            meta-sexpr (z/sexpr meta-loc)
            map-meta-loc (when (z/map? meta-loc)
                           (z/subedit-node meta-loc remove-clerk-keys))]
        (if (or (and map-meta-loc (seq (z/sexpr map-meta-loc)))
                (and (not (keyword? meta-sexpr)) (not (map? meta-sexpr)))
                (and (keyword? meta-sexpr) (not (clerk-namespace? meta-sexpr))))
          ;; keep the meta node, possibly a filtered map, move to the right and repeat
          (recur (z/right (or map-meta-loc meta-loc)))
          ;; remove the meta node, move to the first of the remaining children on the right, repeat
          (recur (z/down (pop-children z)))))

      (deflike-node? (z/node z))
      (recur (-> z z/down z/right))

      :else
      (if-some [sibling (z/right z)]
        (recur sibling)
        (z/root z)))))

(defn text-with-clerk-metadata-removed [code ns-resolver]
  (try
    (-> code p/parse-string-all
        (z/of-node {:auto-resolve ns-resolver})
        zip->node-with-clerk-metadata-removed
        n/string)
    (catch #?(:clj Exception :cljs :default) _ code)))

#_(text-with-clerk-metadata-removed "^::clerk/bar ^{::clerk/foo 'what}\n^ keep \n^{::clerk/bar true :some-key false}  (view that)" {'clerk 'nextjournal.clerk})
#_(text-with-clerk-metadata-removed "^foo    'form" {'clerk 'nextjournal.clerk})
#_(text-with-clerk-metadata-removed "(def ^::clerk/no-cache random-thing (rand-int 1000))" {'clerk 'nextjournal.clerk})
#_(text-with-clerk-metadata-removed "^::clerk/bar [] ;; keep me" {'clerk 'nextjournal.clerk})

(defn update-markdown-blocks [{:as state :keys [md-context]} md]
  (let [doc (markdown/parse* (assoc md-context :content []) md)]
    (-> state
        (assoc :md-context doc)
        (update :blocks conj {:type :markdown
                              :doc (select-keys doc [:type :content :footnotes])}))))




#?(:clj
   (defn sha1-base58 [s]
     (->> s hash/sha1 hash/encode ->base58)))

#?(:cljs
   (defn hash-sha1 [x]
     (let [hasher (goog.crypt.Sha1.)]
       (.update hasher (goog.crypt/stringToUtf8ByteArray (pr-str x)))
       (.digest hasher))))

(defn guess-var
  "An best guess to say if the given `form` defines a var without running
  macroexpansion. Will be refined during analysis."
  [form]
  (when (and (sequential? form)
             (simple-symbol? (first form))
             (simple-symbol? (second form))
             (str/starts-with? (name (first form)) "def"))
    (symbol (str *ns*) (name (second form)))))

(comment
  (guess-var '(def my-range (range 500)))
  (guess-var '(defonce !state (atom {}))))

(defn supports-meta? [x]
  #?(:clj (instance? clojure.lang.IObj x)
     :cljs (satisfies? IMeta x)))

(defn get-block-id [!id->count {:as block :keys [form type doc]}]
  (let [id->count @!id->count
        id (if-let [var (if (contains? block :vars)
                          (:var block)
                          (guess-var form))]
             var
             (let [hash-fn (fn [x]
                             #?(:bb (sha1-base58 (pr-str x))
                                :clj (-> x nippy/fast-freeze sha1-base58)
                                :cljs (hash-sha1 x)))]
               (symbol (str *ns*)
                       (case type
                         :code (str "anon-expr-" (hash-fn (cond-> form
                                                            (supports-meta? form)
                                                            (with-meta {}))))
                         :markdown (str "markdown-" (hash-fn doc))))))]
    (swap! !id->count update id (fnil inc 0))
    (if (id->count id)
      (symbol (str *ns*) (str (name id) "#" (inc (id->count id))))
      id)))

(defn add-block-id [!id->count block]
  (assoc block :id (get-block-id !id->count block)))

#?(:cljs (def Exception js/Error))

#?(:clj
   (defn extract-file
     "Extracts the string file path from the given `resource` to for usage
  on the `:clojure.core/eval-file` form meta key."
     [^java.net.URL resource]
     (case (.getProtocol resource)
       "file" (str (.getFile resource))
       "jar" (str (.getJarEntry ^java.net.JarURLConnection (.openConnection resource))))))

#_(extract-file (clojure.java.io/resource "clojure/core.clj"))
#_(extract-file (clojure.java.io/resource "nextjournal/clerk.clj"))

(defn add-loc [{:as opts :keys [file]} loc form]
  (cond-> form
    (supports-meta? form)
    (vary-meta merge (cond-> loc
                       file (assoc :clojure.core/eval-file
                                           (str #?(:clj (cond-> file
                                                          (instance? java.net.URL file)
                                                          extract-file)
                                                   :cljs file)))))))

(defn add-doc-settings [{:as doc :keys [blocks]}]
  (if-let [first-form (some :form blocks)]
    (merge doc (->doc-settings first-form))
    doc))

(def md-context-keys-to-select
  [:footnotes :title :toc])

(defn parse-clojure-string
  ([s] (parse-clojure-string {} s))
  ([{:as opts :keys [skip-doc?]} s]
   (let [parsed-doc (parse-clojure-string opts
                                          (cond-> {:blocks []
                                                   :md-context markdown/empty-doc}
                                            (:file opts)
                                            (assoc :file (:file opts)))
                                          s)]
     (-> (cond-> parsed-doc
           (not skip-doc?)
           (merge (select-keys (:md-context parsed-doc)
                               md-context-keys-to-select)))
         (dissoc :md-context)
         add-open-graph-metadata
         add-doc-settings)))
  ([{:as opts :keys [skip-doc?]} initial-state s]
   (binding [*ns* (:ns initial-state *ns*)]
     (loop [{:as state :keys [nodes blocks block-settings add-comment-on-line? add-block-id]}

            (assoc initial-state
                   :nodes (:children (try (p/parse-string-all s)
                                          (catch Exception e
                                            (throw (ex-info (str "Clerk failed parsing: "
                                                                 (ex-message e))
                                                            (cond-> {:string s}
                                                              (:file opts) (assoc :file (:file opts)))
                                                            e)))))
                   :add-block-id (partial add-block-id (atom {})))]
       (if-let [node (first nodes)]
         (recur (cond
                  (code-tags (n/tag node))
                  (let [nstring (n/string node)
                        form (try (read-string nstring)
                                  (catch Exception e
                                    (throw (ex-info (str "Clerk failed reading block: "
                                                         (ex-message e)
                                                         e)
                                                    (cond-> {:code (n/string node)}
                                                      (:file opts) (assoc :file (:file opts)))
                                                    e))))
                        loc (-> (meta node)
                                (set/rename-keys {:row :line :end-row :end-line
                                                  :col :column :end-col :end-column})
                                (select-keys [:line :end-line :column :end-column]))
                        next-block-settings (merge-settings
                                             (or (:block-settings state)
                                                 (merge-with merge
                                                             {:nextjournal.clerk/visibility {:code :show :result :show}}
                                                             (parse-global-block-settings form)))
                                             (parse-global-block-settings form))
                        code-block {:type :code
                                    :settings (merge-settings next-block-settings (parse-local-block-settings form))
                                    :text nstring
                                    :form (add-loc opts loc form)
                                    :loc loc}]
                    (when (ns? form)
                      (eval form))
                    (cond-> (-> state
                                (assoc :add-comment-on-line? true)
                                (update :nodes rest)
                                (assoc :block-settings next-block-settings)
                                (update :blocks conj (add-block-id code-block)))
                      (not (contains? state :ns))
                      (assoc :ns *ns*)))
                  (and add-comment-on-line? (whitespace-on-line-tags (n/tag node)))
                  (-> state
                      (assoc :add-comment-on-line? (not (n/comment? node)))
                      (update :nodes rest)
                      (update-in [:blocks (dec (count blocks)) :text] str (-> node n/string str/trim-newline)))

                  (and (not skip-doc?) (n/comment? node))
                  (-> state
                      (assoc :add-comment-on-line? false)
                      (assoc :nodes (drop-while (some-fn n/comment? n/linebreak?) nodes))
                      (update-markdown-blocks (apply str (map (comp remove-leading-semicolons n/string)
                                                              (take-while (some-fn n/comment? n/linebreak?) nodes)))))
                  :else
                  (-> state
                      (assoc :add-comment-on-line? false)
                      (update :nodes rest))))
         (dissoc state :add-block-id :nodes :add-comment-on-line?))))))

(comment
  (parse-clojure-string {:file "foo.clj"} "'code ;; foo\n;; bar")
  (parse-clojure-string "'code , ;; foo\n;; bar")
  (parse-clojure-string "'code\n;; foo\n;; bar")
  (keys (parse-clojure-string (slurp "notebooks/viewer_api.clj")))
  (parse-clojure-string ";; # Hello\n;; ## 👋 Section\n(do 123)\n;; ## 🤚🏽 Section"))

(defn parse-markdown-cell [state opts]
  (assoc (parse-clojure-string opts state (markdown/node->text (first (:nodes state))))
         :nodes (rest (:nodes state))
         ::md-slice []))

(defn runnable-code-block? [{:as block :keys [info language]}]
  (and (code? block)
       info
       (or (empty? language)
           (re-matches #"clj(c?)|clojure" language))
       (not (:nextjournal.clerk/code-listing
             (when-some [parsed (when (and (seq language) (str/starts-with? info language))
                                  (p/parse-string-all (subs info (count language))))]
               (when (n/sexpr-able? parsed)
                 (n/sexpr parsed)))))))

#_(runnable-code-block? {:type :code :language "clojure" :info "clojure"})
#_(runnable-code-block? {:type :code :language "clojure" :info "clojure {:nextjournal.clerk/code-listing true}"})

(defn filter-code-blocks-without-form [doc]
  (update doc :blocks #(filterv (some-fn :form (complement code?)) %)))

(defn parse-markdown-string
  ([s] (parse-markdown-string {} s))
  ([opts s]
   (binding [*ns* *ns*]
     (let [{:as ctx :keys [content]} (markdown/parse* markdown/empty-doc s)]
       (loop [{:as state :keys [nodes] ::keys [md-slice]} (merge
                                                           {:blocks []
                                                            ::md-slice []
                                                            :nodes content
                                                            :md-context ctx}
                                                           (select-keys opts [:file]))]
         (if-some [node (first nodes)]
           (recur
            (if (runnable-code-block? node)
              (-> state
                  (update :blocks #(cond-> % (seq md-slice) (conj {:type :markdown :doc {:type :doc :content md-slice}})))
                  (parse-markdown-cell opts))
              (-> state (update :nodes rest) (update ::md-slice conj node))))

           (-> state
               (update :blocks #(cond-> % (seq md-slice) (conj {:type :markdown :doc {:type :doc :content md-slice}})))
               (dissoc ::md-slice :md-context)
               (merge (select-keys ctx md-context-keys-to-select))
               add-open-graph-metadata
               add-doc-settings)))))))

#_(parse-markdown-string "# Hello\n```\n1\n;; # 1️⃣ Hello\n2\n\n```\nhey\n```\n3\n;; # 2️⃣ Hello\n4\n```\n")

#?(:clj
   (defn parse-file
     ([file] (parse-file {} file))
     ([opts file]
      (-> (if (str/ends-with? file ".md")
            (parse-markdown-string (assoc opts :file file) (slurp file))
            (parse-clojure-string (assoc opts :file file) (slurp file)))))))

(comment
  (parse-file "notebooks/hello.clj")
  (parse-file "notebooks/hello.md")
  (parse-file "notebooks/visibility.clj")
  (parse-file {:skip-doc? true} "notebooks/visibility.clj")
  (parse-file "notebooks/elements.clj")
  (parse-file "notebooks/rule_30.clj")
  (parse-file "notebooks/src/demo/lib.cljc"))

