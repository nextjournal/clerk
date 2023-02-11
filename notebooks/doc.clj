;; # 📇 Clerk Doc
(ns doc
  {:nextjournal.clerk/no-cache true}
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.clerk.config :as config]))

(defmacro doc [name]
  (if config/*in-clerk*
    `(clerk/html
      [:<>
       [:span.font-mono (str (symbol (var ~name)))]
       (clerk/md (:doc (meta (var ~name))))])
    (list 'clojure.repl/doc name)))

(doc inc)

(doc clerk/show!)


