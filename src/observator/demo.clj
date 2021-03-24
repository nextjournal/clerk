;; # Observator Demo!!
(ns observator.demo
  (:require [clojure.string :as str]
            [observator.lib :as obs.lib]))

;; **Dogfooding** the system while constructing it, I'll try to make a
;; little bit of literate commentary. This is *literate* programming.

(defn fix-case [s]
  (obs.lib/fix-case s))

(def long-thing
  (map fix-case (str/split-lines (slurp "/usr/share/dict/words"))))

(count long-thing)

(do ;; slow as well
  (Thread/sleep 5000)
  42)

(def ^:observator/no-cache random-thing
  (rand-int 1000))

(def random-cached-thing
  (rand-int 1000))


(range 1000)
