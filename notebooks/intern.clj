;; # Clojure Intern
(ns intern
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.clerk.analyzer :as clerk.analyzer]))

;; ## Intern at runtime
;;
;; _foreign namespace_
(defn intern-foreign [] (intern (create-ns 'foreign) 'variable 42))

(intern-foreign)

(def a 3)

#_ (clerk.analyzer/analyze '(def b (+ foreign/variable a)))

(def b (+ foreign/variable a))

b

;; _current namespace_

(defn intern-here [] (intern *ns* 'variable 43))

(intern-here)

(def c (+ variable a))

c

(comment
  ;; remove interned vars
  (ns-unmap *ns* 'variable)
  (ns-unmap (find-ns 'foreign) 'variable)

  ;; inspect recorded interns
  (-> @nextjournal.clerk.webserver/!doc
      :blocks (->> (mapcat (comp :nextjournal/interned :result))))

  (ex-data *e)
  (clerk/clear-cache!)

  ;; the first time analyzer finds an unresolvable symbol it won't just complain / won't add it to deps
  (clerk.analyzer/analyze '(def y (+ foo/bar x)))
  ;; analyzer runs _after_ vars being interned or defined, it will resolve them and add to deps
  (def x 1)

  )
