(ns nextjournal.clerk.fixtures.render-fns-broken
  (:require [non-existing-namespace :as s]))

(defn foobar []
  (s/dude))

