;; # Tables 🔢
(ns ^:nextjournal.clerk/no-cache viewers.table
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [next.jdbc :as jdbc]
            [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as v]))

;; ## SQL Queries
(def query-results
  (let [_run-at #inst "2021-05-20T08:28:29.445-00:00"
        ds (jdbc/get-datasource {:dbtype "sqlite" :dbname "chinook.db"})]
    (with-open [conn (jdbc/get-connection ds)]
      (clerk/table (jdbc/execute! conn ["SELECT AlbumId, Bytes, Name, TrackID, UnitPrice FROM tracks"])))))

;; ## Iris Data
^{::clerk/visibility :hide}
(clerk/table (clerk/use-headers (csv/read-csv (slurp "https://gist.githubusercontent.com/netj/8836201/raw/6f9306ad21398ea43cba4f7d537619d0e07d5ae3/iris.csv"))))

(defn words-url []
  (if (.exists (io/file "/usr/share/dict/words"))
    "/usr/share/dict/words"
    "https://gist.githubusercontent.com/wchargin/8927565/raw/d9783627c731268fb2935a731a618aa8e95cf465/words"))

;; ## Words
(clerk/table {:nextjournal/width :full}
             (->> (slurp (words-url))
                  str/split-lines
                  (group-by (comp keyword str/upper-case str first))
                  (into (sorted-map))))

;; ## Table Errors
;; The table viewer will perform normalization and show an error in case of failure:
(clerk/table (set (range 30)))

;; Shows full column names when there are many long column names
(clerk/table {:head
              (-> (mapv (fn [char] (clojure.string/join "" (repeat 30 char)))
                        (map char (range 97 127))))
              :rows
              [(range 97 127)
               (-> (mapv (fn [char] (clojure.string/join "" (repeat 20 char)))
                         (map char (range 97 127))))]})

;; ## Table with images
(clerk/table [[1 2] [3 (javax.imageio.ImageIO/read (java.net.URL. "https://etc.usf.edu/clipart/36600/36667/thermos_36667_sm.gif"))]])

;; ## Table within tables
(clerk/table [[1 2] [3 (clerk/table [[1 2] [3 4]])]])

;; ## Header Formatting
(clerk/table
 (let [head-data [[:key1 "Title A"] [:key2 "Title B"]]
       format-head (fn [[k title]] (clerk/html [:h5.underline.text-xl {:title k} title]))]
   {:rows (map (juxt identity inc) (range 100))
    :head (map format-head head-data)}))

(clerk/with-viewers (clerk/add-viewers [(assoc v/buffered-image-viewer :render-fn '(fn [blob] (v/html [:img {:width "30px" :height "30px" :src (v/url-for blob)}])))])
  (clerk/table
   {:rows (map (juxt identity dec) (range 1 100))
    :head [(javax.imageio.ImageIO/read (java.net.URL. "https://upload.wikimedia.org/wikipedia/commons/1/17/Plus_img_364976.png"))
           (javax.imageio.ImageIO/read (java.net.URL. "https://upload.wikimedia.org/wikipedia/commons/thumb/d/df/OCR-A_char_Hyphen-Minus.svg/543px-OCR-A_char_Hyphen-Minus.svg.png"))]}))

;; ## Custom table viewers
;; override single table components (`:table/markup` `:table/head` `:table/body` `:table/row`)

(def table-head-viewer
  {:name :table/head
   ;; use the transform-fn to manipulate the content of column names
   :transform-fn (v/update-val (partial map (comp (partial str "Column: ") str/capitalize name)))
   ;; use the :render-fn manipulate the table header appeareance
   :render-fn '(fn [header-row {:as opts :keys [path number-col?]}]
                 (v/html [:thead.border-b.border-grey-500.dark:border-slate-700
                          (into [:tr]
                                (map (fn [{:as x :nextjournal/keys [value]}]
                                       [:th [:div.underline [v/inspect x]]])) header-row)]))})

(def missing-viewer
  {:pred #{:nextjournal/missing}
   :render-fn '(fn [x] (v/html [:span.red "N/A"]))})

(defn add-child-viewers [viewer viewers]
  (update viewer :transform-fn (partial comp #(update % :nextjournal/viewers clerk/add-viewers viewers))))

(clerk/with-viewer (add-child-viewers v/table-viewer [table-head-viewer missing-viewer])
  {:col/a [1 2 3 4] :col/b [1 2 3] :col/c [1 2 3]})

;; ## Process Table Headers
(clerk/table {::clerk/opts {:head-transform-fn (comp str/capitalize name)}}
  {:a [1 2 ] :b [3 4]})
