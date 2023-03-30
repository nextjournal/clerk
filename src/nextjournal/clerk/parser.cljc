(ns nextjournal.clerk.parser
  "Clerk's Parser turns Clojure & Markdown files and strings into Clerk documents."
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [clojure.zip]
            [nextjournal.markdown :as markdown]
            [nextjournal.markdown.parser :as markdown.parser]
            [nextjournal.markdown.transform :as markdown.transform]
            [rewrite-clj.node :as n]
            [rewrite-clj.parser :as p]
            [rewrite-clj.zip :as z]))

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
  (if (visibility-marker? form)
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
  (if-some [setting (when (ns? first-form)
                      (get-doc-setting first-form :nextjournal.clerk/error-on-missing-vars))]
    (do (when-not (#{:on :off} setting)
          (throw (ex-info (str "Invalid setting `" (pr-str setting) "` for `:nextjournal.clerk/error-on-missing-vars`. Valid values are `:on` and `:off`.")
                          {:nextjournal.clerk/error-on-missing-vars setting})))
        setting)
    (if (ns? first-form) :on :off)))

(defn ->doc-settings [first-form]
  {:ns? (ns? first-form)
   :error-on-missing-vars (parse-error-on-missing-vars first-form)
   :toc-visibility (or (#{true :collapsed} (:nextjournal.clerk/toc
                                            (merge (-> first-form meta) ;; TODO: deprecate
                                                   (when (ns? first-form)
                                                     (merge (-> first-form second meta)
                                                            (first (filter map? first-form)))))))
                       false)})

(defn ->open-graph [{:keys [title blocks]}]
  (merge {:type "article:clerk"
          :title title
          :description (first (sequence
                               (comp (keep :doc)
                                     (mapcat :content)
                                     (filter (comp #{:paragraph} :type))
                                     (map markdown.transform/->text)) blocks))}
         (some #(get-doc-setting (:form %) :nextjournal.clerk/open-graph) blocks)))

#_(->open-graph
   (nextjournal.clerk.analyzer/analyze-doc
    (parse-file {:doc? true} "notebooks/open_graph.clj")))

(defn add-open-graph-metadata [doc] (assoc doc :open-graph (->open-graph doc)))

;; TODO: Unify with get-doc-settings
(defn add-auto-expand-results [{:as doc :keys [blocks]}]
  (assoc doc :auto-expand-results? (some (fn [{:keys [form]}]
                                           (when (ns? form) (some :nextjournal.clerk/auto-expand-results? form)))
                                         blocks)))

(defn add-css-class [{:as doc :keys [blocks]}]
  (assoc doc :css-class (some (fn [{:keys [form]}]
                                (when (ns? form) (some :nextjournal.clerk/css-class form)))
                              blocks)))

#_(->doc-settings '^{:nextjournal.clerk/toc :boom} (ns foo)) ;; TODO: error

(defn markdown? [{:as block :keys [type]}]
  (contains? #{:markdown} type))

(defn code? [{:as block :keys [type]}]
  (contains? #{:code} type))

(defn add-block-visibility [{:as analyzed-doc :keys [blocks]}]
  (-> (reduce (fn [{:as state :keys [visibility]} {:as block :keys [form]}]
                (let [visibility' (merge visibility (->doc-visibility form))]
                  (cond-> (-> state
                              (update :blocks conj (cond-> block
                                                     (code? block) (assoc :visibility (merge visibility' (->visibility form))))))
                    (code? block) (assoc :visibility visibility'))))
              (assoc analyzed-doc :blocks [] :visibility {:code :show :result :show})
              blocks)
      (dissoc :visibility)))

(def code-tags
  #{:deref :map :meta :list :quote :syntax-quote :reader-macro :set :token :var :vector})

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
  "Takes a map zipper location, returns a new location representing the input map node with all ::clerk namespaced keys removed.
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

(defn markdown-context []
  (update markdown.parser/empty-doc
          :text-tokenizers
          (comp (partial mapv markdown.parser/normalize-tokenizer)
                (partial cons markdown.parser/internal-link-tokenizer))))

(defn parse-markdown
  "Like `n.markdown.parser/parse` but allows to reuse the same context in successive calls"
  [ctx md]
  (markdown.parser/apply-tokens ctx (markdown/tokenize md)))

#_(parse-markdown-string {:doc? true} "# Title\nSome [[internal-link]] to be followed.")

(defn update-markdown-blocks [{:as state :keys [md-context]} md]
  (let [{::markdown.parser/keys [path]} md-context
        doc (parse-markdown md-context md)
        [_ index] path]
    (-> state
        (assoc :md-context doc)
        (update :blocks conj {:type :markdown
                              :doc (-> doc
                                       (select-keys [:type :content :footnotes])
                                       ;; take only new nodes, keep context intact
                                       (update :content subvec (inc index)))}))))

(defn parse-clojure-string
  ([s] (parse-clojure-string {} s))
  ([{:as opts :keys [doc?]} s]
   (let [doc (parse-clojure-string opts {:blocks [] :md-context (markdown-context)} s)]
     (select-keys (cond-> doc doc? (merge (:md-context doc)))
                  [:blocks :title :toc :footnotes])))
  ([{:as _opts :keys [doc?]} initial-state s]
   (loop [{:as state :keys [nodes blocks add-comment-on-line?]} (assoc initial-state :nodes (:children (p/parse-string-all s)))]
     (if-let [node (first nodes)]
       (recur (cond
                (code-tags (n/tag node))
                (-> state
                    (assoc :add-comment-on-line? true)
                    (update :nodes rest)
                    (update :blocks conj {:type :code
                                          :text (n/string node)
                                          :loc (-> (meta node)
                                                   (set/rename-keys {:row :line :end-row :end-line
                                                                     :col :column :end-col :end-column})
                                                   (select-keys [:line :end-line :column :end-column]))}))

                (and add-comment-on-line? (whitespace-on-line-tags (n/tag node)))
                (-> state
                    (assoc :add-comment-on-line? (not (n/comment? node)))
                    (update :nodes rest)
                    (update-in [:blocks (dec (count blocks)) :text] str (-> node n/string str/trim-newline)))

                (and doc? (n/comment? node))
                (-> state
                    (assoc :add-comment-on-line? false)
                    (assoc :nodes (drop-while (some-fn n/comment? n/linebreak?) nodes))
                    (update-markdown-blocks (apply str (map (comp remove-leading-semicolons n/string)
                                                            (take-while (some-fn n/comment? n/linebreak?) nodes)))))
                :else
                (-> state
                    (assoc :add-comment-on-line? false)
                    (update :nodes rest))))
       state))))

#_(parse-clojure-string {:doc? true} "'code ;; foo\n;; bar")
#_(parse-clojure-string "'code\n;; foo\n;; bar")
#_(parse-clojure-string {:doc? true} ";; # Hello\n;; ## 👋 Section\n(do 123)\n;; ## 🤚🏽 Section")

(defn parse-markdown-cell [{:as state :keys [nodes]} opts]
  (assoc (parse-clojure-string opts state (markdown.transform/->text (first nodes)))
         :nodes (rest nodes)
         ::md-slice []))

(defn parse-markdown-string [{:as opts :keys [doc?]} s]
  (let [{:as ctx :keys [content]} (parse-markdown (markdown-context) s)]
    (loop [{:as state :keys [nodes] ::keys [md-slice]} {:blocks [] ::md-slice [] :nodes content :md-context ctx}]
      (if-some [node (first nodes)]
        (recur
         (if (and (code? node) (contains? node :info))
           (-> state
               (update :blocks #(cond-> % (seq md-slice) (conj {:type :markdown :doc {:type :doc :content md-slice}})))
               (parse-markdown-cell opts))
           (-> state (update :nodes rest) (cond-> doc? (update ::md-slice conj node)))))

        (-> state
            (update :blocks #(cond-> % (seq md-slice) (conj {:type :markdown :doc {:type :doc :content md-slice}})))
            (select-keys [:blocks :visibility])
            (merge (when doc?
                     (select-keys ctx [:footnotes :title :toc]))))))))

#_(parse-markdown-string {:doc? true} "# Hello\n```\n1\n;; # 1️⃣ Hello\n2\n\n```\nhey\n```\n3\n;; # 2️⃣ Hello\n4\n```\n")

#?(:clj
   (defn parse-file
     ([file] (parse-file {} file))
     ([opts file] (-> (if (str/ends-with? file ".md")
                        (parse-markdown-string opts (slurp file))
                        (parse-clojure-string opts (slurp file)))
                      (assoc :file file)))))

#_(parse-file {:doc? true} "notebooks/visibility.clj")
#_(parse-file "notebooks/visibility.clj")
#_(parse-file "notebooks/elements.clj")
#_(parse-file "notebooks/markdown.md")
#_(parse-file {:doc? true} "notebooks/rule_30.clj")
#_(parse-file "notebooks/src/demo/lib.cljc")
