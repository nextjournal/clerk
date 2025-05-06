(ns user
  (:require [clj-async-profiler.core :as prof]
            [clojure.java.io :as io]
            [nextjournal.clerk :as clerk]
            [nextjournal.clerk.analyzer :as analyzer]
            [nextjournal.clerk.eval :as eval]
            [nextjournal.clerk.parser :as parser]))

(comment
  ;; start without file watcher & open browser
  (clerk/serve! {:browse? true})

  ;; start without file watcher
  (clerk/serve! {})

  ;; start with file watcher
  (clerk/serve! {:watch-paths ["notebooks" "src"]})

  ;; start with file watcher and show filter function to enable notebook pinning
  (clerk/serve! {:watch-paths ["notebooks" "src"] :show-filter-fn #(clojure.string/starts-with? % "notebooks")})

  (clerk/show! "book.clj")

  (clerk/show! "notebooks/onwards.md")
  (clerk/show! "notebooks/rule_30.clj")
  (clerk/show! "notebooks/how_clerk_works.clj")
  (clerk/show! "notebooks/pagination.clj")
  (clerk/show! "notebooks/paren_soup.clj")
  (clerk/show! "notebooks/recursive.clj")
  (clerk/show! "notebooks/tap.clj")

  (clerk/show! "notebooks/markdown.md")

  (clerk/show! "notebooks/viewer_api.clj")


  (clerk/show! "notebooks/viewers/vega.clj")
  (clerk/show! "notebooks/viewers/plotly.clj")
  (clerk/show! "notebooks/viewers/table.clj")
  (clerk/show! "notebooks/viewers/tex.clj")
  (clerk/show! "notebooks/viewers/markdown.clj")
  (clerk/show! "notebooks/viewers/html.clj")

  (clerk/show! "notebooks/sicmutils.clj")

  (clerk/clear-cache!)

  (do (require 'kaocha.repl)
      (kaocha.repl/run :unit))

  (do
    (clerk/clear-cache!)
    (reset! analyzer/!file->analysis-cache {})
    (prof/profile
      (-> (parser/parse-file {:doc? true} "book.clj")
              analyzer/build-graph
              analyzer/hash))
    nil)
  (prof/serve-ui 8080))

(defmacro with-ex-data [sym body do-block]
  `(try ~body
        (catch Exception e#
          (let [~sym (ex-data e#)]
            ~do-block))))

(defmulti profile :phase)

(defmethod profile :analysis [_opts]
  (let [test-docs [(parser/parse-file {:doc? true} (io/resource "clojure/core.clj"))
                   (parser/parse-file {:doc? true} (io/resource "nextjournal/clerk/analyzer.clj"))
                   (parser/parse-file {:doc? true} (io/resource "nextjournal/clerk.clj"))
                   #_ more?]
        times 5]
    (let [{:keys [time-ms]}
          (eval/time-ms
           (dotimes [_i times]
             (doseq [doc (shuffle test-docs)]
               (-> (analyzer/build-graph doc) analyzer/hash))
             (prn :done/pass _i)))
          mean (/ time-ms (* times (count test-docs)))]
      (println (format "Elapsed mean time: %f msec" mean)))))

;; clj -X:dev:profile :phase :analysis
;; Elapsed mean time: ~1700 msec (iMac i9, main)
;; Elapsed mean time: ~1670 msec (iMac i9, analyzer-improvements)
