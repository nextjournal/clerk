;; # HTML & Hiccup 🧙‍♀️
(ns html (:require [nextjournal.clerk :as clerk]))

(clerk/html "<h3>Ohai, HTML! 👋</h3>")

(clerk/html [:h3 "We "
             [:i "strongly"]
             " prefer hiccup, don't we? ✨"])
