;; # Hello, Clerk 👋

(ns ^:nextjournal.clerk/no-cache hello
  (:require [nextjournal.clerk :as clerk]))

(def answer 41)

(defn red [text] (clerk/html [:strong [:em.ml-1.mr-1.underline.red text]]))

;; the answer is `(inc answer)` with `(red "some approximation")`.
