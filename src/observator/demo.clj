;; # Observator Demo!!
(ns observator.demo
  (:require [clojure.string :as str]
            [observator.core]))

;; **Dogfooding** the system while constructing it, I'll try to make a
;; little bit of literate commentary. This is *literate* programming.

(defn fix-case [s]
  (str/upper-case s))

(def slow-thing
  (do
    (Thread/sleep 500)
    (map fix-case (str/split-lines (slurp "/usr/share/dict/words")))))

(count slow-thing)

(do ;; slow as well
  (Thread/sleep 500)
  42)

(def ^:observator/no-cache random-thing
  (rand-int 1000))

(def random-cached-thing
  (rand-int 1000))


(range 1000)
