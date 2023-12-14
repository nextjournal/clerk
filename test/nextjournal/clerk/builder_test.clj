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

(deftest build-static-app!
  (testing "error when paths are empty (issue #339)"
    (is (thrown-with-msg? ExceptionInfo #"nothing to build" (builder/build-static-app! {:paths []
                                                                                        :report-fn identity}))))
  (testing "error when index is of the wrong type"
    (is (thrown-with-msg? Exception #"`:index` must be" (builder/build-static-app! {:index 0
                                                                                    :report-fn identity})))
    (is (thrown-with-msg? Exception #"`:index` must be" (builder/build-static-app! {:index "not/existing/notebook.clj"
                                                                                    :report-fn identity}))))
  (testing "image is saved to _data dir"
    (is (fs/with-temp-dir [temp-dir {}]
          (builder/build-static-app! {:index "notebooks/viewers/single_image.clj"
                                      :out-path temp-dir
                                      :report-fn identity})
          (first (map fs/file-name (fs/list-dir (fs/file temp-dir "_data") "**.png")))))))

(deftest process-build-opts
  (testing "assigns index when only one path is given"
    (is (= (str (fs/file "notebooks" "rule_30.clj"))
           (:index (builder/process-build-opts {:paths ["notebooks/rule_30.clj"] :expand-paths? true})))))

  (testing "coerces index symbol arg and adds it to expanded-paths"
    (is (= ["book.clj"] (:expanded-paths (builder/process-build-opts {:index 'book.clj :expand-paths? true})))))

  (testing "package option default"
    (is (match? {:package :directory :render-router :fetch-edn}
                (builder/process-build-opts {})))
    (is (match? {:package :single-file :render-router :bundle}
                (builder/process-build-opts {:package :single-file})))))

(deftest doc-url
  (testing "link to same dir unbundled"
    ;; in the unbundled case the current URL on a given notebook is given by
    ;;
    ;; fs-path              |  URL
    ;; ----------------------------------------------------
    ;; path/to/notebook.clj |  path/to/notebok/[index.html]
    (is (= "./../../notebooks/rule_30" ;; NOTE: could also be just "rule_30.html"
           (builder/doc-url {:bundle? false} "notebooks/viewer_api.clj" "notebooks/rule_30"))))

  (testing "respects the mapped index"
    (is (= "./notebooks/rule_30"
           (builder/doc-url {:bundle? false} "index.clj" "notebooks/rule_30")))

    (is (= "./notebooks/rule_30"
           (builder/doc-url {:bundle? false :index "notebooks/path/to/notebook.clj"}
                            "notebooks/path/to/notebook.clj" "notebooks/rule_30")))

    (is (= "./../../../../notebooks/rule_30"
           (builder/doc-url {:bundle? false}
                            "notebooks/path/to/notebook.clj" "notebooks/rule_30"))))

  (testing "bundle case"
    (is (= "#/notebooks/rule_30"
           (builder/doc-url {:bundle? true} "index.clj" "notebooks/rule_30")))))
