;; # 👻 Hiding Clerk Metadata
(ns hiding-clerk-metadata
  {:nextjournal.clerk/toc true}
  (:require [nextjournal.clerk :as clerk]))

;; All metadata on forms in the `nextjournal.clerk` namespace should be hidden from the user, while all other keys should be displayed.

^clojure.lang.PersistentHashMap
^{::clerk/visibility {:result :hide} :please   "keep me"}
{:a 'nice-map} ;; should keep comments here

;; map metadata with all-clerk keys should just not be displayed

^{::clerk/visibility {:result :show} ::clerk/width :wide ::clerk/no-cache true}
(clerk/html
 [:h4
  (apply str (repeat 10 "♦︎"))
  [:span.mx-5 (rand-int 1000)]
  (apply str (repeat 10 "♦︎"))])

;; simple truthy meta like `^::clerk/no-cache` should be removed
^::clerk/no-cache
(rand-int 100) ;; should keep comments here

;; ## Clerk metadata on Vars
;; Should also be hidden

(def ^::clerk/no-cache ^:private random-thing (rand-int 1000))  ;; should keep comments here
(defonce ^{::clerk/no-cache true :doc "this should stay"}  once-random-thing (rand-int 1000))

;; ## Whitespace
;; All whitespace and comments in-between annotations should be preserved:
^{:some/key 123 :and 'this ::clerk/no-cache true}
;; this should be kept
'some-symbol

^::clerk/no-cache
;; this form is not cached
{}

^   :foo
^ {:this   :weird    :map 'is
  ::clerk/visibility {:result :hide}
  :is  :all
     :kept
  123}  ;; should keep comments here
'foo

;; ## Unevals
;; Unevals in between meta expressions should also be kept
^:private #_ keep-me {}
^::clerk/no-cache #_ keep-me {}

;; ## Unreadable forms
;; meta with e.g. unbalanced maps should throw as expected
;;
;;     ^{:a 1 :c}
;;    'what

#_
(comment
  ^{:a 1 :c}
  'what)
