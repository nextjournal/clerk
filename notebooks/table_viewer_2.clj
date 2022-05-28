;; # Table (encore)

;; Leaning into the viewer api to make the table viewer better.

^{:nextjournal.clerk/visibility :hide}
(ns ^:nextjournal.clerk/no-cache table-viewer-2
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [next.jdbc :as jdbc]
            [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :refer :all]))

^{:nextjournal.clerk/viewer :hide-result}
(defn update-table-viewers' [viewers]
  (-> viewers
      (update-viewers {(comp #{:elision} :name) #(assoc % :render-fn '(fn [{:as fetch-opts :keys [total offset unbounded?]} {:keys [num-cols]}]
                                                                        (v/html
                                                                         [v/consume-view-context :fetch-fn (fn [fetch-fn]
                                                                                                             [:tr.border-t.dark:border-slate-700
                                                                                                              [:td.text-center.py-1
                                                                                                               {:col-span #_num-cols 100
                                                                                                                :class (if (fn? fetch-fn)
                                                                                                                         "bg-indigo-50 hover:bg-indigo-100 dark:bg-gray-800 dark:hover:bg-slate-700 cursor-pointer"
                                                                                                                         "text-gray-400 text-slate-500")
                                                                                                                :on-click (fn [_] (when (fn? fetch-fn)
                                                                                                                                    (fetch-fn fetch-opts)))}
                                                                                                               (- total offset) (when unbounded? "+") (if (fn? fetch-fn) " more…" " more elided")]])])))
                       (comp #{string?} :pred) #(assoc % :render-fn (quote v/string-viewer))
                       (comp #{number?} :pred) #(assoc % :render-fn '(fn [x] (v/html [:span.tabular-nums (if (js/Number.isNaN x) "NaN" (str x))])))})
      (add-viewers [{:name :table/markup
                     :render-fn '(fn [head+body opts]
                                   (v/html (into [:table.text-xs.sans-serif.text-gray-900.dark:text-white.not-prose] (v/inspect-children opts) head+body)))}
                    {:name :table/head :render-fn '(fn [header-row {:as opts :keys [path number-col?]}]
                                                     (v/html [:thead.border-b.border-gray-300.dark:border-slate-700
                                                              (into [:tr]
                                                                    (map-indexed (fn [i {v :nextjournal/value}]
                                                                                   ;; TODO: consider not discarding viewer here
                                                                                   (let [title (str (cond-> v (keyword? v) name))]
                                                                                     [:th.relative.pl-6.pr-2.py-1.align-bottom.font-medium
                                                                                      {:title title :class (when (number-col? i) "text-right")}
                                                                                      [:div.flex.items-center title]]))) header-row)]))}
                    {:name :table/body :fetch-opts {:n 20} :render-fn '(fn [rows opts] (v/html (into [:tbody] (map-indexed (fn [idx row] (v/inspect (update opts :path conj idx) row))) rows)))}
                    {:name :table/row :render-fn '(fn [row {:as opts :keys [path number-col?]}]
                                                    (v/html (into [:tr.hover:bg-gray-200.dark:hover:bg-slate-700
                                                                   {:class (if (even? (peek path)) "bg-black/5 dark:bg-gray-800" "bg-white dark:bg-gray-900")}]
                                                                  (map-indexed (fn [idx cell] [:td.pl-6.pr-2.py-1 (when (number-col? idx) {:class "text-right"}) (v/inspect opts cell)])) row)))}
                    {:pred #{:nextjournal/missing} :render-fn '(fn [x] (v/html [:<>]))}])))

^{:nextjournal.clerk/viewer :hide-result}
(def my-table
  (partial with-viewer {:transform-fn (fn [{:as wrapped-value :nextjournal/keys [viewers] :keys [offset path current-path]}]
                                        (if-let [{:keys [head rows]} (normalize-table-data (->value wrapped-value))]
                                          (-> wrapped-value
                                              (assoc :nextjournal/viewer :table/markup)
                                              (update :nextjournal/width #(or % :wide))
                                              (update :nextjournal/viewers update-table-viewers')
                                              (assoc :nextjournal/opts {:num-cols (-> rows first count)
                                                                        :number-col? (mapv number? (first rows))})
                                              (assoc :nextjournal/value (cond->> [(with-viewer :table/body (map (partial with-viewer :table/row) rows))]
                                                                          head (cons (with-viewer :table/head head)))))
                                          (-> wrapped-value
                                              mark-prepared
                                              (assoc :nextjournal/width :wide)
                                              (assoc :nextjournal/value [(prepare wrapped-value)])
                                              (assoc :nextjournal/viewer {:render-fn 'v/table-error}))))}))


;; ## The simplest example, no header.

#_(my-table (repeatedly #(vector 1 2 3)))

(my-table {:head ["num" "foo"] :rows [[1 2] [3 4]]})

;; testing column numeric type
(my-table {:numbers [1.1 2.2] :symbols ['foo 'bar]})

;; padding with missing values
(my-table {:col-1 [1 2 3] :col-2 [1 2]})

;; ## Table Inside a Table
(my-table [[1 2] [3 (my-table [[4 5] [6 7]])]])

;; ## Table with an Image in it
#_(my-table [[1 "and an image"]
             [2 (javax.imageio.ImageIO/read (java.net.URL. "https://etc.usf.edu/clipart/36600/36667/thermos_36667_sm.gif"))]])


;; ## An error
(my-table #{1 2 3})

;; ## More Examples
(def query-results
  (let [_run-at #inst "2021-05-20T08:28:29.445-00:00"
        ds (jdbc/get-datasource {:dbtype "sqlite" :dbname "chinook.db"})]
    (with-open [conn (jdbc/get-connection ds)]
      (my-table (jdbc/execute! conn ["SELECT AlbumId, Bytes, Name, TrackID, UnitPrice FROM tracks"])))))

(my-table (clerk/use-headers (csv/read-csv (slurp "https://gist.githubusercontent.com/netj/8836201/raw/6f9306ad21398ea43cba4f7d537619d0e07d5ae3/iris.csv"))))

(defn words-url []
  (if (.exists (io/file "/usr/share/dict/words"))
    "/usr/share/dict/words"
    "https://gist.githubusercontent.com/wchargin/8927565/raw/d9783627c731268fb2935a731a618aa8e95cf465/words"))

(my-table {:nextjournal/width :full}
             (->> (slurp (words-url))
                  str/split-lines
                  (group-by (comp keyword str/upper-case str first))
                  (into (sorted-map))))

;; ;; The table viewer will perform normalization and show an error in case of failure:
(my-table (set (range 30)))

;; Shows full column names when there are many long column names
(my-table {:head
           (-> (mapv (fn [char] (clojure.string/join "" (repeat 30 char)))
                     (map char (range 97 127))))
           :rows
           [(range 97 127)
            (-> (mapv (fn [char] (clojure.string/join "" (repeat 20 char)))
                      (map char (range 97 127))))]})

(my-table {:a [1 2] :b [3 (my-table [[1 2] [3 4]])]})

#_(def d (comp count-viewers prepare))
