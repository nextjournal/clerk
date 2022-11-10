;; # Test Cases for Auto-Expanding Data Structure Viewer
(ns notebooks.auto-expand
  {:nextjournal.clerk/visibility {:code :hide}
   :nextjournal.clerk/auto-expand-results? true}
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [nextjournal.clerk :as clerk]))

{:a [(range)]}

(map range (range 30))

(map (comp #(map (constantly '🌮) %) range) (range 1 100))

{:foo (range 30)
 :bar (range 20)
 :a-key-with-a-long-name {:a-key-with-another-long-name {:and-another 123456 :and-yet-another 123456}
                          :short-key 1}}

{:foo (vec (repeat 2 {:baz (range 30) :fooze (range 40)})) :bar (range 20)}

(zipmap (range 1000) (map #(* % %) (range 1000)))

(take 5
  (repeatedly (fn []
                {:name (str
                         (rand-nth ["Oscar" "Karen" "Vlad" "Rebecca" "Conrad"]) " "
                         (rand-nth ["Miller" "Stasčnyk" "Ronin" "Meyer" "Black"]))
                 :role (rand-nth [:admin :operator :manager :programmer :designer])
                 :id (gensym)})))

^{::clerk/visibility {:code :hide :result :hide}}
(def words-path "/usr/share/dict/words")

(when-let [words (and (fs/exists? words-path) (slurp words-path))]
  (subs words 0 10100))

(into #{} (map str) (file-seq (clojure.java.io/file "notebooks")))

^{::clerk/visibility {:code :hide :result :hide}}
(defn flat->nested
  [root coll]
  (if-let [children (seq (filter #(= (:id root) (:parent %)) coll))]
    (map #(assoc root :children (flat->nested % coll)) children)
    (list root)))

(let [items (concat [{:id 0 :parent nil :name "item-0"}]
                    (for [x (range 1 5)]
                      {:id x :parent (dec x) :name (format "item-%d" x)}))]
  (flat->nested (-> (filter #(= (:parent %) nil) items) first) items))
