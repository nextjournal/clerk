(ns nextjournal.clerk.datastar-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [nextjournal.clerk.datastar :as datastar]
            [nextjournal.clerk.eval :as eval]
            [nextjournal.clerk.html :as html]
            [nextjournal.clerk.view :as view])
  (:import (java.io ByteArrayInputStream ByteArrayOutputStream EOFException)
           (java.util.zip GZIPInputStream)))

(defn present-string [s]
  (view/doc->viewer (eval/eval-string s)))

(deftest render-notebook
  (let [presented (present-string "(ns datastar-test.render (:require [nextjournal.clerk :as clerk]))
;; # Hello
{:a [1 2 3] :b \"multi\\nline\" :c (range 30)}
(clerk/table [[1 2] [3 4]])
(clerk/html [:div.p-2 (clerk/md \"**md**\")])
(ex-info \"boom\" {:a 1})")
        out (html/->html-str (html/inspect {} presented))]
    (testing "all default viewers used render on the server"
      (is (not (str/includes? out "requires the browser renderer"))))
    (is (str/includes? out "<h1 id=\"hello\">Hello</h1>"))
    (is (str/includes? out "<strong>md</strong>"))
    (is (str/includes? out "boom"))
    (testing "elisions are not fetchable without an `:on-fetch` callback"
      (is (str/includes? out "10 more elided")))))

(deftest tab-state
  (let [presented (present-string "(ns datastar-test.tabs) (def x {:a (range 30)})")
        [id {:keys [hash]}] (first (datastar/result-index presented))
        tab {:results {id {:hash hash :flipped #{[1] [1 0 1 5]}}
                       "gone-result" {:hash "x"}}}]
    (testing "drops state of results that are gone and keeps unchanged ones"
      (is (= {id {:hash hash :flipped #{[1] [1 0 1 5]}}}
             (:results (datastar/prune-tab tab (datastar/result-index presented))))))
    (testing "drops stale paths when a result changed"
      (let [changed (present-string "(ns datastar-test.tabs) (def x {:a 1})")
            [id' {hash' :hash}] (first (datastar/result-index changed))]
        (is (= id id'))
        (is (= {id {:hash hash' :flipped #{[1]}}}
               (:results (datastar/prune-tab tab (datastar/result-index changed)))))))
    (testing "flipped paths toggle the computed expansion"
      (let [{:keys [expanded-at]} ((datastar/result-state-fn tab) id (get-in (datastar/result-index presented) [id :presented]) false)]
        (is (true? (get expanded-at [1])))))))

(deftest sse-event
  (is (= "event: datastar-patch-elements\ndata: elements <main id=\"clerk\">\ndata: elements a</main>\n\n"
         (datastar/sse-event "<main id=\"clerk\">\na</main>")))
  (testing "a carriage return can't break the event framing"
    (is (= 2 (count (re-seq #"data: elements" (datastar/sse-event "a\rb"))))))
  (is (str/starts-with? (datastar/sse-event {:selector "body" :mode "append"} "<script></script>")
                        "event: datastar-patch-elements\ndata: selector body\ndata: mode append\n")))

(defn gunzip
  "Decompresses a gzip stream that hasn't been closed yet."
  [^bytes bs]
  (let [out (ByteArrayOutputStream.)
        in (GZIPInputStream. (ByteArrayInputStream. bs))]
    (try (loop []
           (let [b (.read in)]
             (when-not (neg? b)
               (.write out b)
               (recur))))
         (catch EOFException _))
    (String. (.toByteArray out) "UTF-8")))

(deftest compression
  (is (= "gzip" (:content-encoding (datastar/negotiate-encoder "gzip, deflate, br"))))
  (is (nil? (:content-encoding (datastar/negotiate-encoder nil))))
  (testing "the gzip stream is flushed after every event"
    (let [{:keys [encode close]} (datastar/gzip-encoder)
          chunk-1 (encode (.getBytes "event one\n\n"))
          chunk-2 (encode (.getBytes "event two\n\n"))]
      (close)
      (is (pos? (alength ^bytes chunk-1)))
      (is (str/starts-with? (gunzip (byte-array (concat chunk-1 chunk-2))) "event one\n\nevent two\n\n")))))

(deftest tab-ids
  (is (datastar/valid-tab-id? (datastar/new-tab-id)))
  (is (not (datastar/valid-tab-id? "x');alert(1)//")))
  (is (not (datastar/valid-tab-id? nil))))
