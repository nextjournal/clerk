(ns nextjournal.clerk.router-test
  (:require [babashka.fs :as fs]
            [clojure.edn :as edn]
            [clojure.test :refer [deftest is testing]]
            [nextjournal.clerk.builder :as builder]
            [nextjournal.clerk.router :as router]
            [nextjournal.clerk.viewer :as viewer])
  (:import (java.net URI)))

(deftest build-root
  (testing "index page"
    (is (= "/aoc2025/" (router/build-root "/aoc2025/" "")))
    (is (= "/aoc2025/" (router/build-root "/aoc2025/index.html" "")))
    (is (= "/aoc2025/" (router/build-root "/aoc2025" "")))
    (is (= "/" (router/build-root "/" ""))))
  (testing "nested page"
    (is (= "/aoc2025/" (router/build-root "/aoc2025/src/day04/" "src/day04")))
    (is (= "/aoc2025/" (router/build-root "/aoc2025/src/day04" "src/day04")))
    (is (= "/aoc2025/" (router/build-root "/aoc2025/src/day04/index.html" "src/day04")))
    (is (= "/" (router/build-root "/src/day04/" "src/day04"))))
  (testing "pathname not ending in current-path returns nil"
    (is (nil? (router/build-root "/aoc2025/other/" "src/day04")))
    (is (nil? (router/build-root "/aoc2025/xsrc/day04/" "src/day04")))))

(deftest url-path->doc-path
  (let [router-state {:root "/aoc2025/" :paths #{"" "src/day03" "src/day04"}}]
    (testing "doc of this build"
      (is (= "src/day03" (router/url-path->doc-path router-state "/aoc2025/src/day03")))
      (is (= "src/day03" (router/url-path->doc-path router-state "/aoc2025/src/day03/")))
      (is (= "src/day03" (router/url-path->doc-path router-state "/aoc2025/src/day03/index.html")))
      (is (= "" (router/url-path->doc-path router-state "/aoc2025/")))
      (is (= "" (router/url-path->doc-path router-state "/aoc2025")))
      (is (= "" (router/url-path->doc-path router-state "/aoc2025/index.html"))))
    (testing "url outside the build returns nil"
      (is (nil? (router/url-path->doc-path router-state "/aoc2024/clojure_intro/")))
      (is (nil? (router/url-path->doc-path router-state "/")))
      (is (nil? (router/url-path->doc-path router-state "/aoc20255/src/day03"))))
    (testing "non-doc url under the root returns nil"
      (is (nil? (router/url-path->doc-path router-state "/aoc2025/_data/abc.png")))
      (is (nil? (router/url-path->doc-path router-state "/aoc2025/src/day05/")))))
  (testing "nil paths returns nil"
    (is (nil? (router/url-path->doc-path {:root "/aoc2025/"} "/aoc2025/src/day05/"))))
  (testing "nil root returns nil"
    (is (nil? (router/url-path->doc-path {:root nil} "/aoc2025/src/day03")))))

(deftest doc-path->edn-path
  (is (= "/aoc2025/index.edn" (router/doc-path->edn-path "/aoc2025/" "")))
  (is (= "/aoc2025/src/day04.edn" (router/doc-path->edn-path "/aoc2025/" "src/day04"))))

(deftest doc-path->url-path
  (is (= "/aoc2025/" (router/doc-path->url-path "/aoc2025/" "")))
  (is (= "/aoc2025/src/day04/" (router/doc-path->url-path "/aoc2025/" "src/day04"))))

(defn resolve-path [url-path href]
  (.getPath (.resolve (URI. (str "https://example.com" url-path)) href)))

(def root "/aoc2025/")

(def files
  {"index.clj" ""
   "README.md" "README"
   "src/day04.clj" "src/day04"
   "notebooks/path/to/notebook.clj" "notebooks/path/to/notebook"})

(deftest builder-links-resolve-against-router-url
  (doseq [[file doc-path] files
          :let [page-url (router/doc-path->url-path root doc-path)
                router-state {:root root :paths (set (vals files))}]]
    (testing (str "doc-url links from " file)
      (doseq [target-path (vals files)
              :let [href (builder/doc-url {} file target-path)]]
        (is (= target-path
               (router/url-path->doc-path router-state (resolve-path page-url href)))
            (str href " resolves to " target-path))))
    (testing (str "Index link from " file)
      (let [href (builder/doc-url {:index "index.clj"} file "")]
        (is (= root (resolve-path page-url href)))))
    (testing (str "stored file path from " file)
      (is (= (str root "_data/abc.png")
             (resolve-path page-url (str (viewer/relative-root-prefix-from file) "_data/abc.png")))))))

(defn page-state [html-file]
  (let [state-literal (second (re-find #"(?s)let state = (\".*?\")\.replaceAll" (slurp html-file)))]
    (edn/read-string {:default tagged-literal :readers viewer/data-readers}
                     (read-string state-literal))))

(deftest static-build-matches-router-paths
  (fs/with-temp-dir [out-path {}]
    (builder/build-static-app! {:paths ["notebooks/hello.clj" "notebooks/viewers/html.clj"]
                                :out-path out-path
                                :report-fn identity})
    (let [html-files (map str (fs/glob out-path "**index.html"))
          states (map page-state html-files)
          paths (set (:paths (first states)))]
      (is (= #{"" "notebooks/hello" "notebooks/viewers/html"} paths))
      (doseq [[html-file {:keys [current-path] :as state}] (map vector html-files states)
              :let [url-path (router/doc-path->url-path "/" current-path)]]
        (testing html-file
          (is (= paths (set (:paths state))))
          (is (= :fetch-edn (:render-router state)))
          (is (= (str (fs/path out-path (subs url-path 1) "index.html")) html-file))
          (is (= "/" (router/build-root url-path current-path)))
          (is (fs/exists? (fs/path out-path (subs (router/doc-path->edn-path "/" current-path) 1)))))))))
