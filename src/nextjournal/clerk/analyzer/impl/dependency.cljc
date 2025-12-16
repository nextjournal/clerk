;; Copyright (c) Stuart Sierra, 2012-2015. All rights reserved. The use and
;; distribution terms for this software are covered by the Eclipse Public
;; License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be
;; found in the file epl-v10.html at the root of this distribution. By using
;; this software in any fashion, you are agreeing to be bound by the terms of
;; this license. You must not remove this notice, or any other, from this
;; software.

(ns ^{:author "Stuart Sierra"
      :doc "Bidirectional graphs of dependencies and dependent objects."}
  nextjournal.clerk.analyzer.impl.dependency
  (:require [clojure.set :as set]))

(defprotocol DependencyGraph
  (immediate-dependencies [graph node]
    "Returns the set of immediate dependencies of node.")
  (immediate-dependents [graph node]
    "Returns the set of immediate dependents of node.")
  (transitive-dependencies [graph node]
    "Returns the set of all things which node depends on, directly or
    transitively.")
  (transitive-dependencies-set [graph node-set]
    "Returns the set of all things which any node in node-set depends
    on, directly or transitively.")
  (transitive-dependents [graph node]
    "Returns the set of all things which depend upon node, directly or
    transitively.")
  (transitive-dependents-set [graph node-set]
    "Returns the set of all things which depend upon any node in
    node-set, directly or transitively.")
  (nodes [graph]
    "Returns the set of all nodes in graph."))

(defprotocol DependencyGraphUpdate
  (depend [graph node dep]
    "Returns a new graph with a dependency from node to dep (\"node depends
    on dep\"). Forbids circular dependencies.")
  (remove-edge [graph node dep]
    "Returns a new graph with the dependency from node to dep removed.")
  (remove-all [graph node]
    "Returns a new dependency graph with all references to node removed.")
  (remove-node [graph node]
    "Removes the node from the dependency graph without removing it as a
    dependency of other nodes. That is, removes all outgoing edges from
    node."))

(defn- remove-from-map [amap x]
  (reduce (fn [m [k vs]]
	    (assoc m k (disj vs x)))
	  {} (dissoc amap x)))

(defn- transitive
  "Recursively expands the set of dependency relationships starting
  at (get neighbors x), for each x in node-set"
  [neighbors node-set]
  (loop [unexpanded (mapcat neighbors node-set)
         expanded #{}]
    (if-let [[node & more] (seq unexpanded)]
      (if (contains? expanded node)
        (recur more expanded)
        (recur (concat more (neighbors node))
               (conj expanded node)))
      expanded)))

(declare depends?)

(def set-conj (fnil conj #{}))

;; TODO: make dep graph by id only, should be faster?

(defrecord MapDependencyGraph [dependencies transitive-deps dependents]
  DependencyGraph
  (immediate-dependencies [_graph node]
    (get dependencies node #{}))
  (immediate-dependents [_graph node]
    (get dependents node #{}))
  (transitive-dependencies [_graph node]
    (get transitive-deps node)
    #_(transitive dependencies #{node}))
  (transitive-dependencies-set [_graph node-set]
    (reduce set/union (map #(get transitive-deps %) node-set))
    #_(transitive dependencies node-set))
  (transitive-dependents [_graph node]
    (transitive dependents #{node}))
  (transitive-dependents-set [_graph node-set]
    (transitive dependents node-set))
  (nodes [_graph]
    (clojure.set/union (set (keys dependencies))
                       (set (keys dependents))))
  DependencyGraphUpdate
  (depend [_graph node dep]
    ;; Check for circular dependency using cached transitive deps
    (when (or (= node dep)
              (contains? (get transitive-deps dep #{}) node))
      (throw (ex-info (str "Circular dependency between "
                           (pr-str node) " and " (pr-str dep))
                      {:reason ::circular-dependency
                       :node node
                       :dependency dep})))
    (let [new-trans-for-node (conj (get transitive-deps dep #{}) dep)
          ;; Find all nodes that transitively depend on node
          ;; (we need to update all of them)
          nodes-to-update (conj (transitive dependents #{node}) node)
          ;; minus the nodes that already have dep as a dependency maybe?
          ;; not exactly helping perf
          ;;; nodes-depending-on-dep (filter #(contains? (get transitive-deps %) dep) nodes-to-update)
          #_#__ (when-let [x (seq nodes-depending-on-dep)]
              (prn :nodes-depending-on-dep x))
          ;; _ (prn :nodes-to-update nodes-to-update :one-deps (get transitive-deps 1))
          ;; Update transitive deps for all affected nodes
          updated-trans (reduce
                         (fn [td n]
                           (update td n set/union new-trans-for-node))
                         transitive-deps
                         nodes-to-update #_(apply disj nodes-to-update nodes-depending-on-dep))]
      (MapDependencyGraph.
       (update dependencies node set-conj dep)
       updated-trans
       (update dependents dep set-conj node))))
  (remove-edge [_graph node dep]
    (MapDependencyGraph.
     (update-in dependencies [node] disj dep)
     transitive-deps
     (update-in dependents [dep] disj node)))
  (remove-all [_graph node]
    (MapDependencyGraph.
     (remove-from-map dependencies node)
     transitive-deps
     (remove-from-map dependents node)))
  (remove-node [_graph node]
    (MapDependencyGraph.
     (dissoc dependencies node)
     transitive-deps
     dependents)))

(defn graph "Returns a new, empty, dependency graph." []
  (->MapDependencyGraph {} {} {}))

(comment
  (-> (depend (graph) 1 2)
      (depend 1 4)
      (depend 2 3)
      (depend 3 4)
      #_(transitive-dependencies-set #{1 2}))
  )

#_(defrecord MapDependencyGraph [dependencies dependents]
  DependencyGraph
  (immediate-dependencies [graph node]
    (get dependencies node #{}))
  (immediate-dependents [graph node]
    (get dependents node #{}))
  (transitive-dependencies [graph node]
    (transitive dependencies #{node}))
  (transitive-dependencies-set [graph node-set]
    (transitive dependencies node-set))
  (transitive-dependents [graph node]
    (transitive dependents #{node}))
  (transitive-dependents-set [graph node-set]
    (transitive dependents node-set))
  (nodes [graph]
    (clojure.set/union (set (keys dependencies))
                       (set (keys dependents))))
  DependencyGraphUpdate
  (depend [graph node dep]
    (when (or (= node dep) (depends? graph dep node))
      (throw (ex-info (str "Circular dependency between "
                           (pr-str node) " and " (pr-str dep))
                      {:reason ::circular-dependency
                       :node node
                       :dependency dep})))
    (MapDependencyGraph.
     (update-in dependencies [node] set-conj dep)
     (update-in dependents [dep] set-conj node)))
  (remove-edge [graph node dep]
    (MapDependencyGraph.
     (update-in dependencies [node] disj dep)
     (update-in dependents [dep] disj node)))
  (remove-all [graph node]
    (MapDependencyGraph.
     (remove-from-map dependencies node)
     (remove-from-map dependents node)))
  (remove-node [graph node]
    (MapDependencyGraph.
     (dissoc dependencies node)
     dependents)))

#_(defn graph "Returns a new, empty, dependency graph." []
  (->MapDependencyGraph {} {}))

(defn depends?
  "True if x is directly or transitively dependent on y."
  [graph x y]
  (contains? (transitive-dependencies graph x) y))

(defn dependent?
  "True if y is a dependent of x."
  [graph x y]
  (contains? (transitive-dependents graph x) y))

(defn topo-sort
  "Returns a topologically-sorted list of nodes in graph. Takes an
  optional comparator to provide secondary sorting when the order of
  nodes is ambiguous."
  ([graph]
   (topo-sort (constantly 0) graph))
  ([comp graph]
   (loop [sorted ()
          g graph
          todo (set (filter #(empty? (immediate-dependents graph %))
                            (nodes graph)))]
     (if (empty? todo)
       sorted
       (let [[node & more] (sort #(comp %2 %1) todo)
             deps (immediate-dependencies g node)
             [add g'] (loop [deps deps
                             g g
                             add #{}]
                        (if (seq deps)
                          (let [d (first deps)
                                g' (remove-edge g node d)]
                            (if (empty? (immediate-dependents g' d))
                              (recur (rest deps) g' (conj add d))
                              (recur (rest deps) g' add)))
                          [add g]))]
         (recur (cons node sorted)
                (remove-node g' node)
                (clojure.set/union (set more) (set add))))))))

(def ^:private max-number
  #?(:clj Long/MAX_VALUE
     :cljs js/Number.MAX_VALUE))

(defn topo-comparator
  "Returns a comparator fn which produces a topological sort based on
  the dependencies in graph. Nodes not present in the graph will sort
  after nodes in the graph. Takes an optional secondary comparator to
  provide secondary sorting when the order of nodes is ambiguous."
  ([graph]
   (topo-comparator (constantly 0) graph))
  ([comp graph]
   (let [pos (zipmap (topo-sort comp graph) (range))]
     (fn [a b]
       (let [pos-a (get pos a)
             pos-b (get pos b)]
         (if (and (nil? pos-a) (nil? pos-b))
           (comp a b)
           (compare (or pos-a max-number)
                    (or pos-b max-number))))))))
