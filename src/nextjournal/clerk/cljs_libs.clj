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
   [nextjournal.clerk.analyzer :refer [valuehash]]))

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
   (let [opts {:eof ::eof}]
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
  (or (io/resource (-> (namespace-munge ns)
                       (str/replace "." "/")
                       (str ".cljs")))
      (binding [*out* *err*]
        (println "[clerk] Could not find source for CLJS namespace:" ns))))

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
    (if-let [cljs-sources (not-empty (mapv slurp (keep ns->resource (all-ns state))))]
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
  
  
  )
