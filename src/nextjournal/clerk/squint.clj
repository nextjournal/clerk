(ns nextjournal.clerk.squint
  "Compiles `:render-fn`s to JavaScript modules with Squint for the `:datastar-squint-island` frontend.

  A module imports `squint-cljs/core.js` and exports the compiled `:render-fn` as `render_fn`."
  (:require [clojure.java.io :as io]
            [clojure.walk :as w]
            [nextjournal.clerk.viewer :as v]
            [squint.compiler :as squint]))

(def core-namespaces #{"clojure.core" "cljs.core"})

(defn prepare-form
  "Returns `form` with `clojure.core` and `cljs.core` symbols unqualified.
  Throws for other qualified symbols except `js/` ones."
  [form]
  (w/postwalk (fn [x]
                (if (and (symbol? x) (namespace x))
                  (cond
                    (core-namespaces (namespace x)) (symbol (name x))
                    (= "js" (namespace x)) x
                    :else (throw (ex-info (str "`" x "` in a `:render-fn` isn't supported by the `:datastar-squint-island` frontend. "
                                               "Only `clojure.core` and `js/` symbols are.")
                                          {:symbol x :render-fn form})))
                  x))
              form))

(defn compile-render-fn
  "Returns the JavaScript module for the `:render-fn` `form`."
  [form]
  (let [source (str "(def render-fn " (pr-str (prepare-form form)) ")")]
    (try (squint/compile-string source {})
         (catch Exception e
           (throw (ex-info (str "Squint couldn't compile `:render-fn`: " (ex-message e)) {:render-fn form} e))))))

(defonce ^{:doc "Compiled modules by module id."}
  !modules
  (atom {}))

(defn register-render-fn!
  "Compiles the `:render-fn` `form` once and returns its module id."
  [form]
  (let [id (v/hash-sha1 form)]
    (when-not (contains? @!modules id)
      (swap! !modules assoc id (compile-render-fn form)))
    id))

(defn module-js
  "Returns the JavaScript module with id `id`, or nil if there is none."
  [id]
  (get @!modules id))

(defn core-js []
  (slurp (io/resource "squint/core.js")))

(def reagami-js
  (delay (squint/compile-string (slurp (io/resource "reagami/core.cljc")) {})))

(defn import-map
  "Returns the import map resolving `squint-cljs/core.js`, `reagami` and `clerk-squint/<module id>.js` to urls under `base-url`."
  [base-url]
  (str "{\"imports\":{"
       "\"squint-cljs/core.js\":\"" base-url "squint-core.js\","
       "\"reagami\":\"" base-url "reagami.js\","
       "\"clerk-squint/\":\"" base-url "squint/\"}}"))
