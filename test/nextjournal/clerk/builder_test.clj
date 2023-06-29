(ns nextjournal.clerk.builder-test
  (:require [babashka.fs :as fs]
            [clojure.java.browse :as browse]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [matcher-combinators.test]
            [nextjournal.clerk.builder :as builder])
  (:import (clojure.lang ExceptionInfo)
           (java.io File)))

(deftest url-canonicalize
  (testing "canonicalization of file components into url components"
    (let [dice (str/join (File/separator) ["notebooks" "dice.clj"])]
      (is (= (#'builder/path-to-url-canonicalize dice) (str/replace dice  (File/separator) "/"))))))

(deftest static-app
  (let [url* (volatile! nil)
        original-*ns* *ns*]
    (with-redefs [clojure.java.browse/browse-url (fn [path]
                                                   (vreset! url* path))]
      (testing "browser receives canonical url in this system arch"
        (fs/with-temp-dir [temp {}]
          (let [expected (-> (str/join (java.io.File/separator) [(.toString temp) "index.html"])
                             (str/replace (java.io.File/separator) "/"))]
            (builder/build-static-app! {:browse? true
                                        :paths ["notebooks/hello.clj"]
                                        :out-path temp})
            (is (= expected @url*)))))

      (testing "*ns* isn't changed (#506)"
        (is (= original-*ns* *ns*))))))

(def test-paths ["boo*.clj"])
(def test-paths-fn (fn [] ["boo*.clj"]))

(deftest expand-paths
  (testing "expands glob patterns"
    (let [{paths :expanded-paths} (builder/expand-paths {:paths ["notebooks/*clj"]})]
      (is (> (count paths) 25))
      (is (every? #(str/ends-with? % ".clj") paths))))

  (testing "supports index"
    (is (= {:expanded-paths ["book.clj"]}
           (builder/expand-paths {:index "book.clj"}))))

  (testing "supports paths"
    (is (= {:expanded-paths ["book.clj"]}
           (builder/expand-paths {:paths ["book.clj"]}))))

  (testing "supports paths-fn"
    (is (= {:expanded-paths ["book.clj"]}
           (builder/expand-paths {:paths-fn `test-paths})))
    (is (= {:expanded-paths ["book.clj"]}
           (builder/expand-paths {:paths-fn `test-paths-fn}))))

  (testing "deduplicates index + paths"
    (is (= {:expanded-paths [(str (fs/file "notebooks" "rule_30.clj"))]}
           (builder/expand-paths {:paths ["notebooks/rule_**.clj"]
                                  :index (str (fs/file "notebooks" "rule_30.clj"))}))))

  (testing "supports absolute paths (#504)"
    (is (= {:expanded-paths [(str (fs/file (fs/cwd) "book.clj"))]}
           (builder/expand-paths {:paths [(str (fs/file (fs/cwd) "book.clj"))]}))))

  (testing "invalid args"
    (is (match? {:error #"must set either"}
                (builder/expand-paths {})))
    (is (match? {:error #"must be a qualified symbol pointing at an existing var"}
                (builder/expand-paths {:paths-fn :foo})))
    (is (match? {:error #"must be a qualified symbol pointing at an existing var"}
                (builder/expand-paths {:paths-fn 'foo})))
    (is (match? {:error #"must be a qualified symbol pointing at an existing var"}
                (builder/expand-paths {:paths-fn 'clerk.test.non-existant-name-space/bar})))
    (is (match? {:error #"must be a qualified symbol pointing at an existing var"}
                (builder/expand-paths {:paths-fn 'clojure.core/non-existant-var})))
    (is (match? {:error #"must be a qualified symbol pointing at an existing var"}
                (builder/expand-paths {:paths-fn "hi"})))
    (is (match? {:error #"nothing to build"}
                (builder/expand-paths {:paths []})))
    (is (match? {:error #"An error occured invoking"}
                (builder/expand-paths {:paths-fn 'clojure.core/inc})))
    (is (match? {:error #"must compute to a sequential value."}
                (builder/expand-paths {:paths-fn 'clojure.core/+})))
    (is (match? {:error "`:index` must be either an instance of java.net.URL or a string and point to an existing file"}
                (builder/expand-paths {:index ["book.clj"]})))))

(deftest build-static-app!
  (testing "error when paths are empty (issue #339)"
    (is (thrown-with-msg? ExceptionInfo #"nothing to build" (builder/build-static-app! {:paths []}))))

  (testing "error when index is of the wrong type"
    (is (thrown-with-msg? Exception #"`:index` must be" (builder/build-static-app! {:index 0})))
    (is (thrown-with-msg? Exception #"`:index` must be" (builder/build-static-app! {:index "not/existing/notebook.clj"})))))

(deftest process-build-opts
  (testing "assigns index when only one path is given"
    (is (= (str (fs/file "notebooks" "rule_30.clj"))
           (:index (builder/process-build-opts {:paths ["notebooks/rule_30.clj"] :expand-paths? true})))))

  (testing "coerces index symbol arg and adds it to expanded-paths"
    (is (= ["book.clj"] (:expanded-paths (builder/process-build-opts {:index 'book.clj :expand-paths? true}))))))

#_
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
