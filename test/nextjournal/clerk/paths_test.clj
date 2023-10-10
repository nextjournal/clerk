(ns nextjournal.clerk.paths-test
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [matcher-combinators.test]
            [nextjournal.clerk.paths :as paths]))

(def test-paths ["boo*.clj"])
(def test-paths-fn (fn [] ["boo*.clj"]))

(deftest expand-paths
  (testing "expands glob patterns"
    (let [{paths :expanded-paths} (paths/expand-paths {:paths ["notebooks/*clj"]})]
      (is (> (count paths) 25))
      (is (every? #(str/ends-with? % ".clj") paths))))

  (testing "supports index"
    (is (= {:expanded-paths ["book.clj"]}
           (paths/expand-paths {:index "book.clj"}))))

  (testing "supports paths"
    (is (= {:expanded-paths ["book.clj"]}
           (paths/expand-paths {:paths ["book.clj"]}))))

  (testing "supports paths-fn"
    (is (= {:expanded-paths ["book.clj"]}
           (paths/expand-paths {:paths-fn `test-paths})))
    (is (= {:expanded-paths ["book.clj"]}
           (paths/expand-paths {:paths-fn `test-paths-fn}))))

  (testing "deduplicates index + paths"
    (is (= {:expanded-paths [(str (fs/file "notebooks" "rule_30.clj"))]}
           (paths/expand-paths {:paths ["notebooks/rule_**.clj"]
                                :index (str (fs/file "notebooks" "rule_30.clj"))}))))

  (testing "supports absolute paths (#504)"
    (is (= {:expanded-paths [(str (fs/file (fs/cwd) "book.clj"))]}
           (paths/expand-paths {:paths [(str (fs/file (fs/cwd) "book.clj"))]}))))

  (testing "invalid args"
    (is (match? {:error #"must set either"}
                (paths/expand-paths {})))
    (is (match? {:error #"must be a qualified symbol pointing at an existing var"}
                (paths/expand-paths {:paths-fn :foo})))
    (is (match? {:error #"must be a qualified symbol pointing at an existing var"}
                (paths/expand-paths {:paths-fn 'foo})))
    (is (match? {:error #"must be a qualified symbol pointing at an existing var"}
                (paths/expand-paths {:paths-fn 'clerk.test.non-existant-name-space/bar})))
    (is (match? {:error #"must be a qualified symbol pointing at an existing var"}
                (paths/expand-paths {:paths-fn 'clojure.core/non-existant-var})))
    (is (match? {:error #"must be a qualified symbol pointing at an existing var"}
                (paths/expand-paths {:paths-fn "hi"})))
    (is (match? {:error #"nothing to build"}
                (paths/expand-paths {:paths []})))
    (is (match? {:error #"An error occured invoking"}
                (paths/expand-paths {:paths-fn 'clojure.core/inc})))
    (is (match? {:error #"must compute to a sequential value."}
                (paths/expand-paths {:paths-fn 'clojure.core/+})))
    (is (match? {:error "`:index` must be either an instance of java.net.URL or a string and point to an existing file"}
                (paths/expand-paths {:index ["book.clj"]})))))
