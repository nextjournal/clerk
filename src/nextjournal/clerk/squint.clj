(ns nextjournal.clerk.squint
  "Compiles custom elements defined with `nextjournal.clerk/defelement` to JavaScript modules with Squint."
  (:require [clojure.java.io :as io]
            [nextjournal.clerk.viewer :as v]
            [squint.compiler :as squint]))

(defn compile-element
  "Returns the JavaScript module defining the custom element `tag` with the Squint `defclass` body `forms`."
  [tag forms]
  (let [class-name (symbol tag)]
    (try (:javascript (squint/compile* [(list 'ns 'clerk.element (list :require ["clerk-element" :refer ['ClerkElement 'defineElement]]))
                                        (list* 'defclass class-name forms)
                                        (list 'defineElement tag class-name)]))
         (catch Exception e
           (throw (ex-info (str "Squint couldn't compile element `" tag "`: " (ex-message e)) {:tag tag} e))))))

(defonce ^{:doc "Compiled modules by module id."}
  !modules
  (atom {}))

(defonce ^{:doc "Module ids of the custom elements defined with `nextjournal.clerk/defelement`, by tag."}
  !elements
  (atom {}))

(defn register-element!
  "Compiles the custom element `tag` with the `defclass` body `forms` once and returns its module id."
  [tag forms]
  (let [id (v/hash-sha1 [tag forms])]
    (when-not (contains? @!modules id)
      (swap! !modules assoc id (compile-element tag forms)))
    (swap! !elements assoc tag id)
    id))

(defn element-modules
  "Returns the module ids of all custom elements defined with `nextjournal.clerk/defelement`."
  []
  (sort (vals @!elements)))

(defn module-js
  "Returns the JavaScript module with id `id`, or nil if there is none."
  [id]
  (get @!modules id))

(defn core-js []
  (slurp (io/resource "squint/core.js")))
