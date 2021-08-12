;; # HTML & Hiccup 🧙‍♀️
(require '[nextjournal.clerk.viewer :as v])

(v/html "<h3>Ohai, HTML! 👋</h3>")

(v/html [:h1 "We "
         [:i "strongly"]
         " prefer hiccup, don't we?"])
