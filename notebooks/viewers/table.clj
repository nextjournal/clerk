;; # Tables 🔢
(ns viewers.table
  (:require [clojure.data.csv :as csv]
            [nextjournal.clerk :as clerk]
            [next.jdbc :as j]))

(def query-results
  (let [_run-at #inst "2021-05-20T08:28:29.445-00:00"
        ds (j/get-datasource {:dbtype "sqlite" :dbname "chinook.db"})]
    (with-open [conn (j/get-connection ds)]
      (clerk/table (j/execute! conn ["SELECT AlbumId, Bytes, Name, TrackID, UnitPrice FROM tracks"])))))

(clerk/table (csv/read-csv (slurp "https://gist.githubusercontent.com/netj/8836201/raw/6f9306ad21398ea43cba4f7d537619d0e07d5ae3/iris.csv")))

#_(nextjournal.clerk/show! "notebooks/viewers/table.clj")
