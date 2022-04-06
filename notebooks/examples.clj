;; # 🔭 Clerk Examples
(ns ^:nextjournal.clerk/no-cache nextjournal.clerk.examples
  (:require [nextjournal.clerk :as clerk]
            [clojure.string :as str]
            [rewrite-clj.parser :as p]
            [clojure.java.io :as io]
            [multihash.core :as multihash]
            [multihash.digest :as digest]))


(clerk/set-viewers! [{:name :example
                      :transform-fn (fn [{:keys [form val]}]
                                      (clerk/html [:div (clerk/code (str form " ⇒ " val)) ]))}])

(clerk/with-viewer :example
  {:form '(+ 1 2)
   :val 3})

(do
  (defmacro example
    [& body]
    (when true #_nextjournal.clerk.config/*in-clerk*
          `(clerk/with-viewer {:render-fn '(fn [~'xs ~'opts] (v/html (into [:div.flex.flex-col
                                                                            [:h4 "Clerk Examples"]]
                                                                           (v/inspect-children ~'opts) ~'xs)))
                               #_#_
                               :fetch-fn (fn [_ x#] x#)
                               :transform-fn (fn [ex#] (mapv (partial clerk/with-viewer :example) ex#))}
             (mapv (fn [form# val#] {:form form# :val val#}) ~(mapv (fn [x#] `'~x#) body) ~(vec body)))))
  (macroexpand '(example (+ 1 2))))

(clerk/code (macroexpand '(example (+ 1 2))))

(example
 (clerk/code (macroexpand '(example (+ 1 2))))

 (example (+ 1 2)))

;; - Show all code & results
;; - Results only
;; - Code without docstrings
;; - Var-name & docstring (like cljdoc)
;; - Support linking to vars [[literal-number?]]
;; - Linking back from the browser to the files
#_#_#_#_#_

(defn path->slug [path]
  (-> path
      io/file
      .getName
      (str/replace #"\.(clj(.?)|md)$" "")))

(example
 (path->slug "notebooks/data_mappers.clj")
 (path->slug "notebooks/data_mappers.cljc")
 (path->slug "notebooks/data_mappers.md"))



(path->slug "notebooks/data_mappers.clj")
(path->slug "notebooks/data_mappers.cljc")
(path->slug "notebooks/data_mappers.md")
#_
(do
  
  (defmacro example-2
    [& body]
    (when true #_nextjournal.clerk.config/*in-clerk*
          (clerk/with-viewer
            ''(fn [xs opts] (v/html (into [:div.flex.flex-col
                                           [:h4 "Clerk Examples"]]
                                          (v/inspect-children opts) xs)))
            (into [] (map #(hash-map :val (eval %) :form %)) body))))

  (macroexpand-1 '(example-2 (+ 1 2) (+ 41 1))))

#_#_#_#_

(defn var-name
  "Takes a macroexpanded `form` and returns the name of the var, if it exists."
  [form]
  (when (and (sequential? form)
             (= 'def (first form)))
    (second form)))

(example
 (var-name '(def foo :bar)))

(defn var-dependencies [form]
  (let [var-name (var-name form)]
    (->> form
         (tree-seq sequential? seq)
         (keep #(when (and (symbol? %)
                           (not= var-name %))
                  (resolve %)))

         (into #{}))))

(example
 (var-dependencies '(def foo (clojure.core/fn ([s] (str/includes? (p/parse-string-all s) "hi"))))))

#_(do (clerk/clear-cache!)
      (clerk/show! "notebooks/comment.clj"))
