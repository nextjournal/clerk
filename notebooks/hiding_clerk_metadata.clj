;; # 👻 Hiding Clerk Metadata
(ns hiding-clerk-metadata
  {:nextjournal.clerk/toc true}
  (:require [nextjournal.clerk :as clerk]))

;; All metadata on forms in the `nextjournal.clerk` namespace should be hidden from the user, while all other keys should be displayed.

^clojure.lang.PersistentHashMap
^{::clerk/visibility {:result :hide} :please "keep me"}
{:a 'nice-map}
;; map metadata with all-clerk keys should just not be displayed
^{::clerk/visibility {:result :show} ::clerk/width :wide ::clerk/no-cache true}
(clerk/html
 [:h4
  (apply str (repeat 10 "♦︎"))
  [:span.mx-5 (rand-int 1000)]
  (apply str (repeat 10 "♦︎"))])

;; simple truthy meta `^::clerk/no-cache` should be removed
^::clerk/no-cache
(rand-int 100)

;; ## Meta on Vars
;; `def` forms with meta on the var are not currently addressed
(def ^::clerk/no-cache random-thing-2 (rand-int 1000))

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

;; ## Unreadable forms
;; meta with unbalanced maps should throw as late as read-time not parse-time
;;
;;     ^{:a 1 :c}
;;    'what

#_
(comment
  ^{:a 1 :c}
  'what)
