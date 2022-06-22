;; # ⚛️ Valuehash
(ns valuehash
  (:require [nextjournal.clerk :as clerk]))

(defonce !state (atom (range 100)))
#_(reset! !state (range 100))

;; Clerk will now treat `clojure.core/deref` expresssions seperately in the dependency graph and attempt to compute a hash at runtime based on the value of the expression. This lets Clerk see an updated value for these expressions without needing to opt out of Clerk's caching.

@!state

(map inc @!state)

;; In addition, folks can now specify a `::clerk/hash-fn` that makes the hashing user-extensible. Here, we use it to know when a file has changed.

^{::clerk/hash-fn (fn [_] (clerk/valuehash (slurp "notebooks/hello.md")))
  ::clerk/viewer clerk/md}
(def contents
  (slurp "notebooks/hello.md"))

(clojure.string/split-lines contents)

#_(spit "notebooks/hello.md" "## Hello, Clerk! 👋\n")

#_(do (swap! !state (fn [x] (map inc x)))
      (clerk/recompute!))



