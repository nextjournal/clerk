;; # How Clerk Works 🕵🏻‍♀️
(ns how-clerk-works
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.clerk.hashing :as h]
            [next.jdbc :as jdbc]
            [weavejester.dependency :as dep]))

;; ## File Watching 👀
;; The interface to Clerk is really simple: you save a Clojure file and Clerk takes care of turning that into a notebook.
;; The file watcher library we're using is beholder, originally written by David Nolen for Krell.

;; ## Evaluation ƛ
;; ### Step 1: Parsing
;; First, we parse a given Clojure file using `rewrite-clj`.
(def parsed
  (clerk/parse-file "notebooks/how_clerk_works.clj"))

;; ### Step 2: Analysis
;; Then, each expression is analysed using `tools.analyzer`. A dependency graph, the analyzed form and the originating file is recorded.

(def analyzed
  (h/analyze-file "notebooks/how_clerk_works.clj"))


;; This analysis is done recurively decents into all dependency symbols.

(h/find-location #'nextjournal.clerk.hashing/analyze-file)

(h/find-location #'dep/depend)

(h/find-location  io.methvin.watcher.DirectoryChangeEvent)

(h/find-location java.util.UUID)

(let [{:keys [graph]} (h/build-graph "notebooks/how_clerk_works.clj")]
  (dep/transitive-dependencies graph #'analyzed))


;; ### Step 3: Hashing
;; Then we can use this information to hash each expression.
(def hashes
  (nextjournal.clerk.hashing/hash "notebooks/how_clerk_works.clj"))

;; ### Step 4: Evaluation
;; Clerk uses the hashes as filenames and only re-evaluates forms that haven't been seen before. The cache is using [nippy](https://github.com/ptaoussanis/nippy).
(def rand-fifteen
  (do (Thread/sleep 10)
      (shuffle (range 15))))

;; We can look up the cache key using the var name in the hashes map.
(when-let [form-hash (get hashes #'rand-fifteen)]
  (let [hash (slurp (nextjournal.clerk/->cache-file (str "@" form-hash)))]
    (nextjournal.clerk/thaw-from-cas hash)))

;; As an escape hatch, you can tag a form or var with `::clerk/no-cache` to always reevalaute it. he following form will never be cached.
^:nextjournal.clerk/no-cache (shuffle (range 42))

;; For side effectful functions that should be cached, like a database query, you can add a value like this `#inst` to control when evaluation should happen.
(def query-results
  (let [_run-at #_(java.util.Date.) #inst "2021-10-29T11:43:47.231-00:00"
        ds (next.jdbc/get-datasource {:dbtype "sqlite" :dbname "chinook.db"})]
    (with-open [conn (next.jdbc/get-connection ds)]
      (nextjournal.clerk/table (next.jdbc/execute! conn ["SELECT AlbumId, Bytes, Name, TrackID, UnitPrice FROM tracks"])))))

#_(nextjournal.clerk/show! "notebooks/how_clerk_works.clj")
