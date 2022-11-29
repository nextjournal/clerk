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
  @clerk.analyzer/!interned-symbols
  (reset! nextjournal.clerk.webserver/!doc nextjournal.clerk.webserver/help-doc)
 (ex-data *e)
 (clerk/clear-cache!)
 (clerk.analyzer/analyze '(def b (+ foo/bar a)))
 )
