(ns nextjournal.clerk.cljs-libs
  (:refer-clojure :exclude [remove-ns all-ns])
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.tools.namespace.dependency :as tnsd]
   [clojure.tools.namespace.parse :as tnsp]
   [clojure.walk :as w]
   [edamame.core :as e]
   [nextjournal.clerk.viewer :as v]
   [flatland.ordered.map :as omap]))

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
        (let [ns-decl (with-open [rdr (e/reader (io/reader cljs-file))]
                        (tnsp/read-ns-decl rdr))
              nom (tnsp/name-from-ns-decl ns-decl)
              deps (remove already-loaded-sci-namespaces
                           (tnsp/deps-from-ns-decl ns-decl))]
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
                  (when-let [viewer (v/get-safe v :nextjournal/viewer)]
                    (when-let [r (:require-cljs viewer)]
                      (let [cljs-ns
                            (if (true? r)
                              (->
                               viewer :render-fn
                               ;; at this point, the render-fn has been transformed to a ViewerEval, which contains a :form
                               :form
                               namespace symbol)
                              r)]
                        (require-cljs* state cljs-ns))))
                  v)
                doc)
    (into (omap/ordered-map :cljs-libs
                            (let [resources (keep ns->resource (all-ns state))]
                              (mapv (fn [resource]
                                      (let [code-str (slurp resource)]
                                        (v/->ViewerEval `(load-string ~code-str))))
                                    resources)))
          doc)))

(comment
  ;; [nextjournal.clerk.render.hooks :as hooks]
  (def decl (tnsp/read-ns-decl (edamame.core/reader (java.io.StringReader. (slurp (io/resource "nextjournal/clerk/render/hooks.cljs"))))))
  (tnsp/name-from-ns-decl decl)
  (tnsp/deps-from-ns-decl decl)
  (-> (tnsd/graph)
      (tnsd/depend 'foo 'bar)
      (tnsd/remove-node 'foo)
      (tnsd/topo-sort)))
