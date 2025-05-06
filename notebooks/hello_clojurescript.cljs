;; # Hello ClojureScript 👋

;; This is a ClojureScript Clerk notebook (note the .cljs extension)
;; that's evaluated in Clerk's render env (using SCI).

(ns hello-clojurescript
  (:require [nextjournal.clerk :as clerk]))

(ns-name *ns*)

(.-location js/window)

(clerk/html [:h1 "🐍"])
