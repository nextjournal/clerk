;; # How Clerk Works 🕵🏻‍♀️
^{:nextjournal.clerk/toc true}
(ns how-clerk-works
  (:require [next.jdbc :as jdbc]
            [nextjournal.clerk :as clerk]
            [nextjournal.clerk.eval :as eval]
            [nextjournal.clerk.hashing :as h]
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
  (h/build-graph parsed))


;; This analysis is done recursively, descending into all dependency symbols.

(h/find-location 'nextjournal.clerk.hashing/analyze-file)

(h/find-location `dep/depend)

(h/find-location 'io.methvin.watcher.DirectoryChangeEvent)

(h/find-location 'java.util.UUID)


(let [{:keys [graph]} analyzed]
  (dep/transitive-dependencies graph 'how-clerk-works/analyzed))

;; ### Step 3: Hashing
;; Then we can use this information to hash each expression.
(def hashes
  (h/hash analyzed))

;; ### Step 4: Evaluation
;; Clerk uses the hashes as filenames and only re-evaluates forms that haven't been seen before. The cache is using [nippy](https://github.com/ptaoussanis/nippy).
(def rand-fifteen
  (do (Thread/sleep 10)
      (shuffle (range 15))))

;; We can look up the cache key using the var name in the hashes map.
(when-let [form-hash (get hashes `rand-fifteen)]
  (let [hash (slurp (eval/->cache-file (str "@" form-hash)))]
    (eval/thaw-from-cas hash)))

;; As an escape hatch, you can tag a form or var with `::clerk/no-cache` to always re-evaluate it. The following form will never be cached.
^::clerk/no-cache (shuffle (range 42))

;; For side effectful functions that should be cached, like a database query, you can add a value like this `#inst` to control when evaluation should happen.
(def query-results
  (let [_run-at #_(java.util.Date.) #inst "2021-05-20T08:28:29.445-00:00"
        ds (next.jdbc/get-datasource {:dbtype "sqlite" :dbname "chinook.db"})]
    (with-open [conn (next.jdbc/get-connection ds)]
      (clerk/table (next.jdbc/execute! conn ["SELECT AlbumId, Bytes, Name, TrackID, UnitPrice FROM tracks"])))))
