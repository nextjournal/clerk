(ns nextjournal.clerk.fixtures.require-cljs-bundle-dedupe-1
  (:require [nextjournal.clerk :as clerk]))

(def my-viewer
  {:require-cljs true
   :render-fn 'nextjournal.clerk.fixtures.render-fns/id})

(clerk/with-viewer my-viewer
  [1 2 3])

(comment
  (require '[babashka.fs :as fs])
  (fs/exists? "test/nextjournal/clerk/fixtures/require_cljs_bundle_dedupe_1.clj")
  (clerk/build! {:paths ["test/nextjournal/clerk/fixtures/require_cljs_bundle_dedupe_1.clj"
                         "test/nextjournal/clerk/fixtures/require_cljs_bundle_dedupe_2.clj"]
                 :package :single-file})
  (def build (slurp "public/build/index.html"))
  (= 1 (re-find #"(prn ::identity)" build))
  )
