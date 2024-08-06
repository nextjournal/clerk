(ns nextjournal.clerk.cljs-libs
  (:refer-clojure :exclude [remove-ns all-ns])
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.tools.namespace.dependency :as tnsd]
   [clojure.tools.namespace.parse :as tnsp]
   [edamame.core :as e]
   [nextjournal.clerk.viewer :as v]))

(def ^:private already-loaded-sci-namespaces
  '#{user
     nextjournal.clerk.render
     nextjournal.clojure-mode.commands
     reagent.ratom
     nextjournal.clerk
     reagent.core
     nextjournal.clerk.parser
     nextjournal.clerk.viewer
     clojure.core clojure.set
     cljs.math
     clojure.edn
     nextjournal.clojure-mode.keymap
     reagent.debug
     cljs.repl
     clojure.repl
     applied-science.js-interop
     nextjournal.clerk.render.navbar
     clojure.string
     nextjournal.clojure-mode.extensions.eval-region
     clojure.walk
     nextjournal.clerk.render.editor
     nextjournal.clerk.render.hooks
     nextjournal.clerk.render.code
     clojure.template})

(defonce ^:private cljs-graph (atom (tnsd/graph)))

(defn- ns->resource [ns]
  (or (io/resource (-> (namespace-munge ns)
                       (str/replace "." "/")
                       (str ".cljs")))
      (binding [*out* *err*]
        (println "[clerk] Could not find source for CLJS namespace:" ns))))

(defn require-cljs [& nss]
  (doseq [libspec nss]
    (let [[ns {:keys [as]}] (if (symbol? libspec)
                              [libspec]
                              (let [ns (first libspec)
                                    opts (apply hash-map (rest libspec))]
                                [ns opts]))]
      (when as
        (alias as ns))
      (when-not (contains? already-loaded-sci-namespaces libspec)
        (when-let [cljs-file (ns->resource ns)]
          (let [ns-decl (with-open [rdr (e/reader (io/reader cljs-file))]
                          (tnsp/read-ns-decl rdr))
                nom (tnsp/name-from-ns-decl ns-decl)
                deps (remove already-loaded-sci-namespaces
                             (tnsp/deps-from-ns-decl ns-decl))]
            (run! require-cljs deps)
            (swap! cljs-graph (fn [graph]
                                (reduce (fn [acc dep]
                                          (tnsd/depend acc nom dep))
                                        (tnsd/remove-node graph ns)
                                        (or (seq deps)
                                            [::orphan]))))
            nil))))))

(defn all-ns []
  (remove #(= ::orphan %) (tnsd/topo-sort @cljs-graph)))

(defn remove-ns [ns]
  (swap! cljs-graph (fn [graph]
                      (-> graph
                          (tnsd/remove-all ns))))
  nil)

(defn update-blocks [doc]
  (update doc :blocks (fn [blocks]
                        (concat
                         (let [resources (keep ns->resource (all-ns))]
                           (map (fn [resource]
                                  (let [code-str (slurp resource)]
                                    {:type :code
                                     :text (pr-str `(nextjournal.clerk/eval-cljs-str ~code-str))
                                     :result {:nextjournal/value (v/eval-cljs-str code-str)}
                                     :settings #:nextjournal.clerk{:visibility {:code :hide, :result :hide}}}))
                                resources))
                         blocks))))

(comment
  (nextjournal.clerk/eval-cljs-str "(+ 1 2 3)")
  ;; [nextjournal.clerk.render.hooks :as hooks]
  (def decl (tnsp/read-ns-decl (edamame.core/reader (java.io.StringReader. (slurp (io/resource "nextjournal/clerk/render/hooks.cljs"))))))
  (tnsp/name-from-ns-decl decl)
  (tnsp/deps-from-ns-decl decl)
  @cljs-graph

  (-> (tnsd/graph)
      (tnsd/depend 'foo 'bar)
      (tnsd/remove-node 'foo)
      (tnsd/topo-sort)))
