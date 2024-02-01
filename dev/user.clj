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

  (require '[clj-async-profiler.core :as prof]
           'nextjournal.clerk.eval-test
           'nextjournal.clerk.viewer-test
           'nextjournal.clerk.analyzer-test)
  (prof/profile
   (time (clojure.test/run-tests 'nextjournal.clerk.eval-test
                                 'nextjournal.clerk.viewer-test
                                 'nextjournal.clerk.analyzer-test)))
  ;; ~16.6s
  (prof/serve-ui 8080))

(defmacro with-ex-data [sym body do-block]
  `(try ~body
        (catch Exception e#
          (let [~sym (ex-data e#)]
            ~do-block))))

(defmulti profile :phase)

(defmethod profile :analysis [_opts]
  (let [test-docs [(parser/parse-file {:doc? true} (io/resource "clojure/core.clj"))
                   #_ more?]
        times 5]
    (let [{:keys [time-ms]}
          (eval/time-ms
           (dotimes [_i times]
             (doseq [doc (shuffle test-docs)]
               (-> (analyzer/build-graph doc) analyzer/hash))))
          mean (/ time-ms (* times (count test-docs)))]
      (println (format "Elapsed mean time: %f msec" mean)))))

;; clj -X:dev:profile :phase :analysis
;; Elapsed mean time: 4300,223637 msec (main)
;; Elapsed mean time: 4866,353578 msec (analyzer-improvements)
;; Elapsed mean time: 4594,642337 msec (analyzer-improvements - dep reverse map lookup)
;; Elapsed mean time: 3784,830730 msec (analyzer-improvements - dep reverse map lookup / single entry last wins)
