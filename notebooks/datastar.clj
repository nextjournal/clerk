;; # 🚀 Datastar Frontend
;; Clerk's default frontend renders this notebook on the server and pushes
;; it to the browser via Datastar.
(ns datastar
  {:nextjournal.clerk/toc true}
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.clerk.datastar :as ds]
            [nextjournal.clerk.experimental :as cx]))

;; ## Synced atoms
;; Moving the slider or typing updates the atom on the JVM, which
;; recomputes the notebook and pushes the new view to the browser.
^{::clerk/sync true ::clerk/viewer cx/slider}
(defonce !num (atom 42))

^{::clerk/sync true ::clerk/viewer cx/text-input}
(defonce !greeting (atom "Hello, Datastar"))

{:num @!num :greeting @!greeting :doubled (* 2 @!num)}

;; ## Evaluating on the server
;; `ds/eval-attrs` evaluates a form in this namespace when clicked.
(clerk/html [:div.flex.gap-2.font-sans
             [:button.px-3.py-1.rounded.bg-indigo-600.text-white (ds/eval-attrs '(swap! !num dec)) "−1"]
             [:button.px-3.py-1.rounded.bg-indigo-600.text-white (ds/eval-attrs '(swap! !num inc)) "+1"]
             [:button.px-3.py-1.rounded.bg-slate-200 (ds/eval-attrs '(reset! !num 0)) "reset"]])

;; Links to other notebooks navigate without a page load, e.g. [Hello](/notebooks/hello).

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
