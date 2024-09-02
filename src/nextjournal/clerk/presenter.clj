(ns nextjournal.clerk.presenter
  "Notebook to hold & show a single value, used by `nextjournal.clerk/present!`."
  {:no-doc true
   :nextjournal.clerk/no-cache true
   :nextjournal.clerk/visibility {:code :hide
                                  :result :hide}}
  (:require [nextjournal.clerk.viewer :as v]))

(defonce !val
  (atom nil))

#_(reset! !val nil)

^{:nextjournal.clerk/visibility {:result :show}}
(def presented
  (if-let [val @!val]
    val
    (v/html [:p "Missing value."])))

(comment
  (nextjournal.clerk/present! 42)

  (nextjournal.clerk/present! (nextjournal.clerk/html [:h1 "Hi, Clerk! 👋"]))

  (nextjournal.clerk/present! (range 100)))
