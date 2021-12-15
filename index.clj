;; # Hello Clerk

^:nextjournal.clerk/no-cache
(ns index
  (:require [nextjournal.clerk :as clerk]))

;; with clerk helper
(clerk/html
 [:ul
  [:li [:a.underline {:href (clerk/doc-url "notebooks/paren_soup.clj")} "Soup"]]
  [:li [:a.underline {:href (clerk/doc-url "notebooks/markdown.md")} "MD"]]])

(comment
  (clerk/serve! {})
  (clerk/clear-cache!)
  (clerk/build-static-app!
   {:paths ["index.clj" "notebooks/paren_soup.clj" "notebooks/how_clerk_works.clj" "notebooks/rule_30.clj" "notebooks/markdown.md"]
    :bundle?  true
    :live-js? true
    ;;:path-prefix "build/"
    })

  )

