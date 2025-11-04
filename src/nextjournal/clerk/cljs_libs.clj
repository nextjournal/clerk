(ns nextjournal.clerk.cljs-libs
  (:refer-clojure :exclude [remove-ns all-ns])
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [edamame.core :as e]
   [nextjournal.clerk.always-array-map :as aam]
   [nextjournal.clerk.analyzer :refer [valuehash]]
   [nextjournal.clerk.viewer :as v]
   [rewrite-clj.node :as rnode]
   [rewrite-clj.parser :as rparse]
   [weavejester.dependency :as tnsd]))

(def already-loaded-sci-namespaces
  (atom '#{applied-science.js-interop
           cljs.math
           cljs.repl
           clojure.core
           cljs.core
           clojure.edn
           clojure.math
           clojure.repl
           clojure.set
           clojure.string
           clojure.template
           clojure.tools.reader.reader-types
           clojure.walk
           goog.object
           goog.string
           goog.string.format
           goog.array
           nextjournal.clerk
           nextjournal.clerk.parser
           nextjournal.clerk.render
           nextjournal.clerk.render.code
           nextjournal.clerk.render.editor
           nextjournal.clerk.render.hooks
           nextjournal.clerk.render.navbar
           nextjournal.clerk.viewer
           nextjournal.clojure-mode
           nextjournal.clojure-mode.commands
           nextjournal.clojure-mode.extensions.eval-region
           nextjournal.clojure-mode.keymap
           nextjournal.markdown
           nextjournal.markdown.transform
           reagent.core
           reagent.debug
           reagent.ratom
           user
           clojure.pprint
           cljs.pprint}))

(defn- ns-decl?
  "Returns true if form is a (ns ...) declaration."
  [form]
  (and (list? form) (= 'ns (first form))))

(defn- read-ns-decl
  ([rdr]
   (let [opts {:eof ::eof
               :read-cond :allow
               :features #{:cljs}}]
     (loop []
       (let [form (e/parse-next rdr opts)]
         (cond
           (ns-decl? form) form
           (= ::eof form) nil
           :else (recur)))))))

(defn deps-from-ns-decl [parsed-ns-decl]
  (keep (fn [req]
          (when-not (:as-alias req)
            (let [lib (:lib req)]
              (when (symbol? lib)
                lib)))) (:requires parsed-ns-decl)))

#_(deps-from-ns-decl (e/parse-ns-form '(ns foo (:require [foo] [bar :as-alias dude]))))

(defn name-from-ns-decl [parsed-ns-decl]
  (:current parsed-ns-decl))

(defn- new-cljs-state []
  (atom {:graph (tnsd/graph)
         :loaded-libs #{}}))

(defn- ns->resource [ns]
  (let [prefix (-> (namespace-munge ns)
                   (str/replace "." "/"))
        exts ["cljs" "cljc"]]
    (or (some #(io/resource (str prefix "." %))
              exts)
        (throw (ex-info (str "Could not find source for CLJS namespace: " ns)
                        {:ns ns
                         :already-loaded-sci-namespaces @already-loaded-sci-namespaces})))))

(comment
  (ns->resource 'viewers.viewer-with-cljs-source)
  (ns->resource 'viewers.viewer-lib)
  )

(defn- require-cljs* [state & nss]
  (doseq [ns nss]
    (when-not (or (contains? @already-loaded-sci-namespaces ns)
                  (contains? (:loaded-libs @state) ns))
      (when-let [cljs-file (ns->resource ns)]
        (let [ns-decl (with-open [^java.io.Closeable rdr (e/reader (io/reader cljs-file))]
                        (read-ns-decl rdr))
              ns-decl (e/parse-ns-form ns-decl)
              nom (name-from-ns-decl ns-decl)
              deps (remove @already-loaded-sci-namespaces
                           (deps-from-ns-decl ns-decl))]
          (apply require-cljs* state deps)
          (swap! state (fn [state]
                              (-> state
                                  (update :graph
                                          (fn [graph]
                                            (reduce (fn [acc dep]
                                                      (tnsd/depend acc nom dep))
                                                    (tnsd/remove-node graph ns)
                                                    (or (seq deps)
                                                        [::orphan]))))
                                  (update :loaded-libs conj ns))))
          nil)))))

(defn all-ns [cljs-state]
  (remove #(= ::orphan %) (tnsd/topo-sort (:graph @cljs-state))))

;;;; Selection of reader conditionals, borrowed from clj-kondo.impl.utils

(defn- first-non-whitespace [nodes]
  (some #(when (and (not (rnode/whitespace-or-comment? %))
                    (not= :uneval (rnode/tag %)))
           %)
        nodes))

(defn- process-reader-conditional [node langs]
  (if (and node
           (= :reader-macro (rnode/tag node))
           (let [sv (-> node :children first :string-value)]
             (str/starts-with? sv "?")))
    (let [children (-> node :children last :children)]
      (loop [[k & ts] children
             default nil]
        (let [kw (:k k)
              default (or default
                          (when (= :default kw)
                            (first-non-whitespace ts)))]
          (if (contains? langs kw)
            (first-non-whitespace ts)
            (if (seq ts)
              (recur ts default)
              default)))))
    node))

(declare select-langs)

(defn- select-lang-children [node langs]
  (if-let [children (:children node)]
    (let [new-children (reduce
                        (fn [acc node]
                          (let [splice? (= "?@" (some-> node :children first :string-value))]
                            (if-let [processed (select-langs node langs)]
                              (if splice?
                                (into acc (:children processed))
                                (conj acc processed))
                              acc)))
                        []
                        children)]
      (assoc node :children
             new-children))
    node))

(defn- select-langs
  ([node langs]
   (when-let [processed (process-reader-conditional node langs)]
     (select-lang-children processed langs))))

;;;; End selection of reader conditionals

(defn- slurp-resource [resource]
  (if (str/ends-with? (str resource) ".cljc")
    (-> (slurp resource)
        (rparse/parse-string-all)
        (select-langs #{:cljs})
        str)
    (slurp resource)))

(defn prepend-required-cljs [doc {:keys [dedupe-cljs]}]
  (let [state (new-cljs-state)
        cljs-namespaces ((:store!-cljs-namespace doc))]
    (run! #(require-cljs* state %) cljs-namespaces)
    (if-let [cljs-sources (not-empty
                           (filter #(or (not dedupe-cljs)
                                        (when-not (contains? @dedupe-cljs %)
                                          (swap! dedupe-cljs conj %)
                                          true))
                                   (mapv slurp-resource (keep ns->resource (all-ns state)))))]
      (-> doc
          ;; make sure :cljs-libs is the first key, so these are read + evaluated first
          (aam/assoc-before :cljs-libs (mapv (fn [code-str] (v/->render-eval `(load-string ~code-str))) cljs-sources))
          (aam/assoc-before :nextjournal.clerk/remount (valuehash :sha1 cljs-sources)))
      doc)))

;;;; Scratch

(comment
  (-> (:graph @(new-cljs-state))
      (tnsd/depend 'foo 'bar)
      (tnsd/transitive-dependencies 'foo))
  (slurp-resource (io/resource "viewers/viewer_lib.cljc"))
  )
