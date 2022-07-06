;; # ⚛️ Valuehash
(ns valuehash
  (:require [nextjournal.clerk :as clerk]))

(defonce !state (atom (range 100)))
#_(reset! !state (range 100))

;; Clerk will now treat `clojure.core/deref` expresssions seperately in the dependency graph and attempt to compute a hash at runtime based on the value of the expression. This lets Clerk see an updated value for these expressions without needing to opt out of Clerk's caching.

@!state

(map inc @!state)

#_(do (swap! !state (fn [x] (map inc x)))
      (clerk/recompute!))


;; ### TODO
;; * [x] clean up `find-location`
;; * [x] fix deref'ing vars, e.g. `@(var contents)`
;; * [x] Uncomment & fix `no-cache?` tests
;; * [x] Restore & fix _"defcached should be treated like a normal def"_
;; * [x] Fix deps graph for vars
;; * [ ] Make `::clerk/no-cache` expression get value semantics for result (so downstream expressions only re-evaluate when the result changed)
