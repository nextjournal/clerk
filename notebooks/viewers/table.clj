;; # Tables 🔢
(ns ^:nextjournal.clerk/no-cache viewers.table
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [next.jdbc :as jdbc]
            [nextjournal.clerk :as clerk]))

(def query-results
  (let [_run-at #inst "2021-05-20T08:28:29.445-00:00"
        ds (jdbc/get-datasource {:dbtype "sqlite" :dbname "chinook.db"})]
    (with-open [conn (jdbc/get-connection ds)]
      (clerk/table (jdbc/execute! conn ["SELECT AlbumId, Bytes, Name, TrackID, UnitPrice FROM tracks"])))))

(clerk/table (clerk/use-headers (csv/read-csv (slurp "https://gist.githubusercontent.com/netj/8836201/raw/6f9306ad21398ea43cba4f7d537619d0e07d5ae3/iris.csv"))))

(defn words-url []
  (if (.exists (io/file "/usr/share/dict/words"))
    "/usr/share/dict/words"
    "https://gist.githubusercontent.com/wchargin/8927565/raw/d9783627c731268fb2935a731a618aa8e95cf465/words"))

(clerk/table {:nextjournal/width :full}
             (->> (slurp (words-url))
                  str/split-lines
                  (group-by (comp keyword str/upper-case str first))
                  (into (sorted-map))))

;; ;; The table viewer will perform normalization and show an error in case of failure:
(clerk/table (set (range 30)))

;; Shows full column names when there are many long column names
(clerk/table {:head
              (-> (mapv (fn [char] (clojure.string/join "" (repeat 30 char)))
                        (map char (range 97 127))))
              :rows
              [(range 97 127)
               (-> (mapv (fn [char] (clojure.string/join "" (repeat 20 char)))
                         (map char (range 97 127))))]})

;; Table with images
(clerk/table [[1 2] [3 (javax.imageio.ImageIO/read (java.net.URL. "https://etc.usf.edu/clipart/36600/36667/thermos_36667_sm.gif"))]])

;; Table with a table
(clerk/table [[1 2] [3 (clerk/table [[1 2] [3 4]])]])
