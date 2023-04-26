(ns nextjournal.clerk.builder-test
  (:require [babashka.fs :as fs]
            [clojure.java.browse :as browse]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [nextjournal.clerk.builder :as builder])
  (:import (clojure.lang ExceptionInfo)
           (java.io File)))

(deftest url-canonicalize
  (testing "canonicalization of file components into url components"
    (let [dice (str/join (File/separator) ["notebooks" "dice.clj"])]
      (is (= (#'builder/path-to-url-canonicalize dice) (str/replace dice  (File/separator) "/"))))))

(deftest static-app
  (let [url* (volatile! nil)]
    (with-redefs [clojure.java.browse/browse-url (fn [path]
                                                   (vreset! url* path))]
      (testing "browser receives canonical url in this system arch"
        (fs/with-temp-dir [temp {}]
          (let [expected (-> (str/join (java.io.File/separator) [(.toString temp) "index.html"])
                             (str/replace (java.io.File/separator) "/"))]
            (builder/build-static-app! {:browse? true
                                        :paths ["notebooks/hello.clj"]
                                        :out-path temp})
            (is (= expected @url*))))))))

(deftest expand-paths
  (testing "expands glob patterns"
    (let [paths (builder/expand-paths {:paths ["notebooks/*clj"]})]
      (is (> (count paths) 25))
      (is (every? #(str/ends-with? % ".clj") paths))))

  (testing "supports index"
    (is (= ["book.clj"] (builder/expand-paths {:index "book.clj"}))))

  (testing "supports paths"
    (is (= ["book.clj"] (builder/expand-paths {:paths ["book.clj"]}))))

  (testing "deduplicates index + paths"
    (is (= [(str (fs/file "notebooks" "rule_30.clj"))]
           (builder/expand-paths {:paths ["notebooks/rule_**.clj"]
                                  :index (str (fs/file "notebooks" "rule_30.clj"))}))))

  (testing "invalid args"
    (is (thrown-with-msg? ExceptionInfo #"must set either"
                          (builder/expand-paths {})))
    (is (thrown-with-msg? ExceptionInfo #"must be a qualified symbol pointing at an existing var"
                          (builder/expand-paths {:paths-fn :foo})))
    (is (thrown-with-msg? ExceptionInfo #"must be a qualified symbol pointing at an existing var"
                          (builder/expand-paths {:paths-fn 'foo})))
    (is (thrown-with-msg? ExceptionInfo #"must be a qualified symbol pointing at an existing var"
                          (builder/expand-paths {:paths-fn 'clerk.test.non-existant-name-space/bar})))
    (is (thrown-with-msg? ExceptionInfo #"must be a qualified symbol pointing at an existing var"
                          (builder/expand-paths {:paths-fn 'clojure.core/non-existant-var})))
    (is (thrown-with-msg? ExceptionInfo #"must be a qualified symbol pointing at an existing var"
                          (builder/expand-paths {:paths-fn "hi"})))
    (is (thrown-with-msg? ExceptionInfo #"nothing to build"
                          (builder/expand-paths {:paths []})))
    (is (thrown-with-msg? ExceptionInfo #"An error occured invoking"
                          (builder/expand-paths {:paths-fn 'clojure.core/inc})))
    (is (thrown-with-msg? ExceptionInfo #"`:paths-fn` must compute sequential value"
                          (builder/expand-paths {:paths-fn 'clojure.core/+})))
    (is (thrown? ExceptionInfo (builder/expand-paths {:index ["book.clj"]})))))

(deftest build-static-app!
  (testing "error when paths are empty (issue #339)"
    (is (thrown-with-msg? ExceptionInfo #"nothing to build" (builder/build-static-app! {:paths []})))))

(deftest process-build-opts
  (testing "assigns index when only one path is given"
    (is (= (str (fs/file "notebooks" "rule_30.clj"))
           (:index (builder/process-build-opts {:paths ["notebooks/rule_30.clj"] :expand-paths? true})))))

  (testing "coerces index symbol arg and adds it to expanded-paths"
    (is (= ["book.clj"] (:expanded-paths (builder/process-build-opts {:index 'book.clj :expand-paths? true}))))))


(deftest doc-url
  (testing "link to same dir unbundled"
    (is (= "./../notebooks/rule_30.html" ;; NOTE: could also be just "rule_30.html"
           (builder/doc-url {:bundle? false} [{:file "notebooks/viewer_api.clj"} {:file "notebooks/rule_30.clj"}] "notebooks/viewer_api.clj" "notebooks/rule_30.clj"))))

  (testing "respects the mapped index"
    (is (= "./notebooks/rule_30.html"
           (builder/doc-url {:bundle? false} [{:file "index.clj"} {:file "notebooks/rule_30.clj"}] "index.clj" "notebooks/rule_30.clj"))))

  (testing "bundle case"
    (is (= "#/notebooks/rule_30.clj"
           (builder/doc-url {:bundle? true} [{:file "notebooks/index.clj"} {:file "notebooks/rule_30.clj"}] "index.clj" "notebooks/rule_30.clj")))))
