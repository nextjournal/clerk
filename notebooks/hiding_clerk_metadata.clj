;; # 👻 Hiding Clerk Metadata
(ns hiding-clerk-metadata
  {:nextjournal.clerk/toc true
   ;; disable hiding metadata for the whole notebook
   #_#_ :nextjournal.clerk/show-meta true}
  (:require [nextjournal.clerk :as clerk]))

;; All metadata on forms in the `nextjournal.clerk` namespace should be hidden from the user, while all other keys should be displayed.

^clojure.lang.PersistentHashMap
^{::clerk/visibility {:result :hide} :please   "keep me"}
{:a 'nice-map}
;; map metadata with all-clerk keys should just not be displayed
^{::clerk/visibility {:result :show} ::clerk/width :wide ::clerk/no-cache true}
(clerk/html
 [:h4
  (apply str (repeat 10 "♦︎"))
  [:span.mx-5 (rand-int 1000)]
  (apply str (repeat 10 "♦︎"))])

;; simple truthy meta like `^::clerk/no-cache` should be removed
^::clerk/no-cache
(rand-int 100)

;; ## Clerk metadata on Vars
;; Should also be hidden

(def ^::clerk/no-cache ^:private random-thing (rand-int 1000))
(defonce ^{::clerk/no-cache true :doc "this should stay"}  once-random-thing (rand-int 1000))

;; ## Whitespace
;; All whitespace and comments in-between annotations should be preserved:
^{:some/key 123 :and 'this ::clerk/no-cache true}
;; this should be kept
'some-symbol

^   :foo
^ {:this   :weird    :map 'is
  ::clerk/visibility {:result :hide}
  :is  :all
     :kept
  123}
'foo

;; ## Locally overridden defaults
;; with a map `{::clerk/show-meta true-or-false}` we can control metadata display:
;; hidden
^::clerk/no-cache 'one

{::clerk/show-meta true}
;; shown
^::clerk/no-cache 'two

{::clerk/show-meta false}

;; hidden again
^::clerk/no-cache 'three

;; ## Unreadable forms
;; meta with e.g. unbalanced maps should throw as expected
;;
;;     ^{:a 1 :c}
;;    'what

#_
(comment
  ^{:a 1 :c}
  'what)
