(ns nextjournal.clerk.parser
  "Clerk's Parser turns Clojure & Markdown files and strings into Clerk documents."
  (:require [clojure.core :as core]
            [clojure.set :as set]
            [clojure.string :as str]
            [nextjournal.markdown :as markdown]
            [nextjournal.markdown.parser :as markdown.parser]
            [nextjournal.markdown.transform :as markdown.transform]
            [rewrite-clj.node :as n]
            [rewrite-clj.parser :as p]))

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
  (or (when (ns? form) (merge (-> form second meta key) (some key form)))
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

(defn ->doc-settings [first-form]
  {:ns? (ns? first-form)
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

#_(->doc-settings '^{:nextjournal.clerk/toc :boom} (ns foo)) ;; TODO: error

(defn add-block-visibility [{:as analyzed-doc :keys [blocks]}]
  (-> (reduce (fn [{:as state :keys [visibility]} {:as block :keys [var form type]}]
                (let [visibility' (merge visibility (->doc-visibility form))]
                  (cond-> (-> state
                              (update :blocks conj (cond-> block
                                                     (= type :code) (assoc :visibility (merge visibility' (->visibility form))))))
                    (= type :code) (assoc :visibility visibility'))))
              (assoc analyzed-doc :blocks [] :visibility {:code :show :result :show})
              blocks)
      (dissoc :visibility)))

(def code-tags
  #{:deref :map :meta :list :quote :reader-macro :set :token :var :vector})

(def whitespace-on-line-tags
  #{:comment :whitespace :comma})

(defn parse-clojure-string
  ([s] (parse-clojure-string {} s))
  ([opts s] (parse-clojure-string opts {:blocks []} s))
  ([{:as _opts :keys [doc?]} initial-state s]
   (loop [{:as state :keys [nodes blocks visibility add-comment-on-line?]} (assoc initial-state :nodes (:children (p/parse-string-all s)))]
     (if-let [node (first nodes)]
       (recur (cond
                (code-tags (n/tag node))
                (-> state
                    (assoc :add-comment-on-line? true)
                    (update :nodes rest)
                    (update :blocks conj {:type :code
                                          :text (n/string node)
                                          :loc (-> (meta node)
                                                   (set/rename-keys {:row :line
                                                                     :col :column})
                                                   (select-keys [:line :column]))}))

                (and add-comment-on-line? (whitespace-on-line-tags (n/tag node)))
                (-> state
                    (assoc :add-comment-on-line? (not (n/comment? node)))
                    (update :nodes rest)
                    (update-in [:blocks (dec (count blocks)) :text] str (-> node n/string str/trim-newline)))

                (and doc? (n/comment? node))
                (-> state
                    (assoc :add-comment-on-line? false)
                    (assoc :nodes (drop-while (some-fn n/comment? n/linebreak?) nodes))
                    (update :blocks conj {:type :markdown
                                          :doc (-> (apply str (map (comp remove-leading-semicolons n/string)
                                                                   (take-while (some-fn n/comment? n/linebreak?) nodes)))
                                                   markdown/parse
                                                   (select-keys [:type :content]))}))
                :else
                (-> state
                    (assoc :add-comment-on-line? false)
                    (update :nodes rest))))
       (merge (select-keys state [:blocks])
              (when doc?
                (-> {:content (into []
                                    (comp (filter (comp #{:markdown} :type))
                                          (mapcat (comp :content :doc)))
                                    blocks)}
                    markdown.parser/add-title+toc
                    (select-keys [:title :toc]))))))))

#_(parse-clojure-string {:doc? true} "'code ;; foo\n;; bar")
#_(parse-clojure-string "'code , ;; foo\n;; bar")
#_(parse-clojure-string "'code\n;; foo\n;; bar")
#_(keys (parse-clojure-string {:doc? true} (slurp "notebooks/viewer_api.clj")))

(defn code-cell? [{:as node :keys [type]}]
  (and (= :code type) (contains? node :info)))

(defn parse-markdown-cell [{:as state :keys [nodes]}]
  (assoc (parse-clojure-string {:doc? true} state (markdown.transform/->text (first nodes)))
         :nodes (rest nodes)
         ::md-slice []))

(defn parse-markdown-string [{:keys [doc?]} s]
  (let [{:keys [content toc title]} (markdown/parse s)]
    (loop [{:as state :keys [nodes] ::keys [md-slice]} {:blocks [] ::md-slice [] :nodes content}]
      (if-some [node (first nodes)]
        (recur
         (if (code-cell? node)
           (-> state
               (update :blocks #(cond-> % (seq md-slice) (conj {:type :markdown :doc {:type :doc :content md-slice}})))
               parse-markdown-cell)

           (-> state (update :nodes rest) (cond-> doc? (update ::md-slice conj node)))))

        (-> state
            (update :blocks #(cond-> % (seq md-slice) (conj {:type :markdown :doc {:type :doc :content md-slice}})))
            (select-keys [:blocks :visibility])
            (merge (when doc? {:title title :toc toc})))))))

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
