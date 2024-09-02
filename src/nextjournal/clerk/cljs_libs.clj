(ns nextjournal.clerk.cljs-libs
  (:refer-clojure :exclude [remove-ns all-ns])
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [weavejester.dependency :as tnsd]
   [clojure.walk :as w]
   [edamame.core :as e]
   [nextjournal.clerk.viewer :as v]
   [nextjournal.clerk.always-array-map :as aam]
   [nextjournal.clerk.analyzer :refer [valuehash]]
   [rewrite-clj.node :as rnode]
   [rewrite-clj.parser :as rparse]))

(def ^:private already-loaded-sci-namespaces
  '#{applied-science.js-interop
     cljs.math
     cljs.repl
     clojure.core
     clojure.edn
     clojure.repl
     clojure.set
     clojure.string
     clojure.template
     clojure.walk
     nextjournal.clerk
     nextjournal.clerk.parser
     nextjournal.clerk.render
     nextjournal.clerk.render.code
     nextjournal.clerk.render.editor
     nextjournal.clerk.render.hooks
     nextjournal.clerk.render.navbar
     nextjournal.clerk.viewer
     nextjournal.clojure-mode.commands
     nextjournal.clojure-mode.extensions.eval-region
     nextjournal.clojure-mode.keymap
     reagent.core
     reagent.debug
     reagent.ratom
     user})

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
  (filter symbol? (map :lib (:requires parsed-ns-decl))))

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
        (binding [*out* *err*]
          (println "[clerk] Could not find source for CLJS namespace:" ns)))))

(comment
  (ns->resource 'viewers.viewer-with-cljs-source)
  (ns->resource 'viewers.viewer-lib)
  )

(defn require-cljs* [state & nss]
  (doseq [ns nss]
    (when-not (or (contains? already-loaded-sci-namespaces ns)
                  (contains? (:loaded-libs @state) ns))
      (when-let [cljs-file (ns->resource ns)]
        (let [ns-decl (with-open [^java.io.Closeable rdr (e/reader (io/reader cljs-file))]
                        (read-ns-decl rdr))
              ns-decl (e/parse-ns-form ns-decl)
              nom (name-from-ns-decl ns-decl)
              deps (remove already-loaded-sci-namespaces
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

(defn first-non-whitespace [nodes]
  (some #(when (and (not (rnode/whitespace-or-comment? %))
                    (not= :uneval (rnode/tag %)))
           %)
        nodes))

(defn process-reader-conditional [node langs splice?]
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

(defn select-lang-children [node langs]
  (if-let [children (:children node)]
    (let [new-children (reduce
                        (fn [acc node]
                          (let [splice? (= "?@" (some-> node :children first :string-value))]
                            (if-let [processed (select-langs node langs splice?)]
                              (if splice?
                                (into acc (:children processed))
                                (conj acc processed))
                              acc)))
                        []
                        children)]
      (assoc node :children
             new-children))
    node))

(defn select-langs
  ([node langs] (select-langs node langs nil))
  ([node langs splice?]
   (when-let [processed (process-reader-conditional node langs splice?)]
     (select-lang-children processed langs))))

;;;; End selection of reader conditionals

(defn slurp-resource [resource]
  (if (str/ends-with? (str resource) ".cljc")
    (-> (slurp resource)
        (rparse/parse-string-all)
        (select-langs #{:cljs})
        str)
    (slurp resource)))

(defn prepend-required-cljs [doc]
  (let [state (new-cljs-state)]
    (w/postwalk (fn [v]
                  (if-let [viewer (v/get-safe v :nextjournal/viewer)]
                    (if-let [r (:require-cljs viewer)]
                      (let [cljs-ns (if (true? r)
                                      ;; at this point, the render-fn has been transformed to a `ViewerFn`, which contains a :form
                                      (-> viewer :render-fn :form namespace symbol)
                                      r)]
                        (require-cljs* state cljs-ns))
                      v)
                    v))
                doc)
    (if-let [cljs-sources (not-empty (mapv slurp-resource (keep ns->resource (all-ns state))))]
      (-> doc
          ;; make sure :cljs-libs is the first key, so these are read + evaluated first
          (aam/assoc-before :cljs-libs (mapv (fn [code-str] (v/->ViewerEval `(load-string ~code-str))) cljs-sources))
          (aam/assoc-before :nextjournal.clerk/remount (valuehash :sha1 cljs-sources)))
      doc)))

;;;; Scratch

(comment
  (-> (:graph @(new-cljs-state))
      (tnsd/depend 'foo 'bar)
      (tnsd/transitive-dependencies 'foo))
  (slurp-resource (io/resource "viewers/viewer_lib.cljc"))
  )
