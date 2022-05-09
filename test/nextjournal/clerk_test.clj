(ns nextjournal.clerk-test
  (:require [babashka.fs :as fs]
            [clojure.java.browse :as browse]
            [clojure.string :as str]
            [clojure.test :refer :all]
            [matcher-combinators.test :refer [match?]]
            [nextjournal.clerk :as clerk]
            [nextjournal.clerk.hashing :as hashing]
            [nextjournal.clerk.view :as view])
  (:import (java.io File)))

(deftest url-canonicalize
  (testing "canonicalization of file components into url components"
    (let [dice (str/join (File/separator) ["notebooks" "dice.clj"])]
      (is (= (#'clerk/path-to-url-canonicalize dice) (str/replace dice  (File/separator) "/"))))))

(deftest static-app
  (let [url* (volatile! nil)]
    (with-redefs [clojure.java.browse/browse-url (fn [path]
                                                   (vreset! url* path))]
      (testing "browser receives canonical url in this system arch"
        (fs/with-temp-dir [temp {}]
          (let [expected (-> (str/join (java.io.File/separator) [(.toString temp) "index.html"])
                             (str/replace (java.io.File/separator) "/"))]
            (clerk/build-static-app! {:paths ["notebooks/hello.clj"]
                                      :out-path temp})
            (is (= expected @url*))))))))

(deftest eval-string
  (testing "hello 42"
    (is (match? {:blocks [{:type :code,
                           :result {:nextjournal/value 42}}]}
                (clerk/eval-string "(+ 39 3)")))
    (is (match? {:blocks [{:type :code,
                           :result {:nextjournal/value 41}}]}
                (clerk/eval-string "(+ 39 2)")))
    (is (match? {:blocks [{:type :code,
                           :result {:nextjournal/value 41}}]}
                (clerk/eval-string "^:nextjournal.clerk/no-cache (+ 39 2)"))))

  (testing "all values gets blob id"
    (let [{:keys [blocks blob->result]} (clerk/eval-string "(ns user) (inc 41) 42")]
      (is (match? [map?
                   {:result {:nextjournal/value 42}}
                   {:result {:nextjournal/value 42}}]
                  blocks))
      (is (= 0 (count (into [] (comp (map key) (filter nil?)) blob->result))))))

  (testing "the 'previous results' cache takes first precedence"
    (let [doc (hashing/parse-clojure-string "(inc 41)")
          {:keys [blob->result]} (clerk/eval-doc doc)
          [blob-id {v :nextjournal/value}] (first blob->result)]
      (is (= v 42))
      (is (match? {:blocks [{:result {:nextjournal/value -4}}]}
                  (clerk/eval-doc {blob-id {:nextjournal/value -4}} doc)))))

  (testing "handling binding forms i.e. def, defn"
    ;; ensure "some-var" is a variable in whatever namespace we're running in
    (testing "the variable is properly defined"
      (let [{:keys [blocks]} (clerk/eval-string "(ns ^:nextjournal.clerk/no-cache my-test-ns) (def some-var 99)")]
        (is (match? [map?
                     {:type :code,
                      :result {:nextjournal/value {::clerk/var-from-def var?}}}]
                    blocks))
        (is (= 99 @(find-var 'my-test-ns/some-var))))))

  (testing "random expression gets cached"
    (is (= (clerk/eval-string "(ns my-random-test-ns) (java.util.UUID/randomUUID)")
           (clerk/eval-string "(ns my-random-test-ns) (java.util.UUID/randomUUID)"))))

  (testing "random expression that cannot be serialized in nippy gets cached in memory"
    (let [{:as result :keys [blob->result]} (clerk/eval-string "(ns my-random-test-ns) {inc (java.util.UUID/randomUUID)}")]
      (is (= result
             (clerk/eval-string blob->result "(ns my-random-test-ns) {inc (java.util.UUID/randomUUID)}")))))

  (testing "random expression doesn't get cached with no-cache"
    (is (not= (clerk/eval-string "(ns ^:nextjournal.clerk/no-cache my-random-test-ns) (java.util.UUID/randomUUID)")
              (clerk/eval-string "(ns ^:nextjournal.clerk/no-cache my-random-test-ns) (java.util.UUID/randomUUID)"))))

  (testing "random expression that cannot be frozen with nippy gets cached via in-memory cache"
    (let [code "(ns my-random-test-ns) {:my-fn inc :my-uuid (java.util.UUID/randomUUID)}"
          result  (clerk/eval-string code)
          result' (clerk/eval-string (:blob->result result) code)
          extract-my-uuid #(-> % :blocks last :result :nextjournal/value :my-uuid)]
      (is (= (extract-my-uuid result)
             (extract-my-uuid result')))))

  (testing "old values are cleared from in-memory cache"
    (let [{:keys [blob->result]} (clerk/eval-string "(ns my-random-test-ns) ^:nextjournal.clerk/no-cache {inc (java.util.UUID/randomUUID)}")]
      (is (= 2 (count (:blob->result (clerk/eval-string blob->result "(ns my-random-test-ns) {inc (java.util.UUID/randomUUID)}")))))))

  (testing "defonce returns correct result on subsequent evals (when defonce would eval to nil)"
    (clerk/eval-string "(ns ^:nextjournal.clerk/no-cache my-defonce-test-ns) (defonce state (atom {}))")
    (is (match? {:blocks [map? {:result {:nextjournal/value {::clerk/var-from-def var?}}}]}
                (clerk/eval-string "(ns ^:nextjournal.clerk/no-cache my-defonce-test-ns) (defonce state (atom {}))"))))

  (testing "assigning viewers from form meta"
    (is (match? {:blocks [{:result {:nextjournal/viewer fn?}}]}
                (clerk/eval-string "^{:nextjournal.clerk/viewer nextjournal.clerk/table} (def markup [:h1 \"hi\"])")))
    (is (match? {:blocks [{:result {:nextjournal/viewer :html}}]}
                (clerk/eval-string "^{:nextjournal.clerk/viewer :html} (def markup [:h1 \"hi\"])")))))

(deftest eval-string+doc->viewer
  (testing "assigns correct width from viewer function opts"
    (is (match? [{:nextjournal/width :wide}
                 {:nextjournal/width :full}]
                (-> "^{:nextjournal.clerk/visibility :hide} (ns clerk-test-width
  (:require [nextjournal.clerk :as clerk]))

(clerk/html {::clerk/width :wide} [:div.bg-red-200 [:h1 \"Wide Hiccup\"]])

(clerk/table {::clerk/width :full} {:a [1] :b [2] :c [3]})"
                    clerk/eval-string
                    view/doc->viewer
                    :nextjournal/value
                    :blocks))))

  (testing "assigns the correct width from form meta"
    (is (match? [{:nextjournal/width :full}
                 {:nextjournal/width :wide}]
                (-> "^{:nextjournal.clerk/visibility :hide} (ns clerk-test-width)

^{:nextjournal.clerk/viewer :table :nextjournal.clerk/width :full}
(def dataset
  [[1 2] [3 4]])

^{:nextjournal.clerk/viewer :html :nextjournal.clerk/width :wide}
[:div.bg-red-200 [:h1 \"Wide Hiccup\"]]
"
                    clerk/eval-string
                    view/doc->viewer
                    :nextjournal/value
                    :blocks)))))

(deftest expand-paths-test
  (let [paths (clerk/expand-paths ["notebooks/*clj"])]
    (is (> (count paths) 25))
    (is (every? #(str/ends-with? % ".clj") paths))))
