;; # Tables 🔢
(ns ^:nextjournal.clerk/no-cache viewers.table
  (:require [clojure.data.csv :as csv]
            [clojure.string :as str]
            [next.jdbc :as jdbc]
            [nextjournal.clerk :as clerk]))

(def query-results
  (let [_run-at #inst "2021-05-20T08:28:29.445-00:00"
        ds (jdbc/get-datasource {:dbtype "sqlite" :dbname "chinook.db"})]
    (with-open [conn (jdbc/get-connection ds)]
      (clerk/table (jdbc/execute! conn ["SELECT AlbumId, Bytes, Name, TrackID, UnitPrice FROM tracks"])))))

(clerk/table (clerk/use-headers (csv/read-csv (slurp "https://gist.githubusercontent.com/netj/8836201/raw/6f9306ad21398ea43cba4f7d537619d0e07d5ae3/iris.csv"))))

(clerk/table {:nextjournal/width :full}
             (->> (slurp "/usr/share/dict/words")
                  str/split-lines
                  (group-by (comp keyword str/upper-case str first))
                  (into (sorted-map))))

;; The table viewer will perform normalization and show an error in case of failure:
(clerk/table (set (range 30)))

#_(nextjournal.clerk/show! "notebooks/viewers/table.clj")
