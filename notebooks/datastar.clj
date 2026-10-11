;; # 🚀 Datastar Frontend
;; Serve this notebook with `(clerk/serve! {:frontend :datastar})` to see it
;; rendered on the server and pushed to the browser via Datastar.
(ns datastar
  (:require [nextjournal.clerk :as clerk]))

;; ## Collections
;; Click a triangle to expand, click _more…_ to fetch elided values.
(def data
  {:vector [1 2 3]
   :string "multi\nline\nstring"
   :range (range 30)
   :nested {:numbers (range 100)
            :set #{:a :b :c}
            :symbols '[foo bar baz]}})

(apply str (repeat 100 "abc"))

;; ## Tables
(clerk/table {:n (range 45)
              :square (map #(* % %) (range 45))})

;; ## HTML
(clerk/html [:div.p-4.rounded.bg-indigo-50 "Hiccup with a nested value: " (clerk/md "**markdown**")])

(clerk/row (clerk/html [:div "left"]) (clerk/html [:div "right"]))

;; ## Images
(clerk/image "trees.png")

;; ## Errors
(ex-info "An error as a value" {:some :data})
