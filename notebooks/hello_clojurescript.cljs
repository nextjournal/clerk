;; # Hello ClojureScript 👋

;; This is a ClojureScript Clerk notebook (note the .cljs extension)
;; that's evaluated in Clerk's render env (using SCI).

(ns hello-clojurescript
  (:require [nextjournal.clerk.viewer :as v]))

(.-location js/window)

;; TODO: support ns requires
#_(v/html [:h1 "🐍"])
