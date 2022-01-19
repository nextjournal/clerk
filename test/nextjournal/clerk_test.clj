(ns nextjournal.clerk-test
  (:require [babashka.fs :as fs]
            [clojure.java.browse :as browse]
            [clojure.string :as str]
            [clojure.test :refer :all]
            [matcher-combinators.test :refer [match?]]
            [nextjournal.clerk :as clerk]
            [nextjournal.clerk.hashing :as hashing])
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

(defn- lookup-var-in-*ns* [var-name]
  (find-var (symbol (str *ns*) var-name)))


(deftest read+eval-cached
  (testing "basic eval'ing will give a result with a hash"
    (let [->hash {'{:x (inc 10)} "fake-hash"}
          first-run (clerk/read+eval-cached {} ->hash #{:show} {:form '{:x (inc 10)}})]
      (is (match? {:nextjournal/value            {:x 11}
                   :nextjournal.clerk/visibility #{:show}
                   :nextjournal/blob-id          "fake-hash"}
                  first-run))
      (testing "the 'previous results' cache takes first precedence"
        #_(is (match? {:nextjournal/value :hacked}
                      (clerk/read+eval-cached {"fake-hash" :hacked}
                                              ->hash
                                              #{:show}
                                              "{:x (inc 10)}"))))))

  (testing "eval'ing stores results in a cache"
    ;; ensure "a-var" is a variable in whatever namespace we're running in
    (intern *ns* 'a-var 0)
    (is (match? {:nextjournal/value 1}
                (clerk/read+eval-cached {} {} #{:show} {:form '(inc a-var)})))

    ;; sneakily change the var under the hood
    (alter-var-root (lookup-var-in-*ns* "a-var") inc)

    #_(testing "the expression is cached and we don't see the change to `a-var`"
        (is (match? {:nextjournal/value 1}
                    (clerk/read+eval-cached {} {} #{:show} {:form '(inc a-var)}))))

    ;; TODO: pass hash param or move this to higher level call
    (testing "with caching off, we get the freshly altered result"
      (with-redefs [hashing/no-cache? (constantly true)]
        (is (match? {:nextjournal/value 2}
                    (clerk/read+eval-cached {} {} #{:show} {:form '(inc a-var)})))))))


(deftest eval-string
  (testing "hello 42"
    (is (match? [{:type :code,
                  :result {:nextjournal/value 42}}]
                (clerk/eval-string "(+ 39 3)")))
    (is (match? [{:type :code,
                  :result {:nextjournal/value 41}}]
                (clerk/eval-string "(+ 39 2)")))
    (is (match? [{:type :code,
                  :result {:nextjournal/value 41}}]
                (clerk/eval-string "^:nextjournal.clerk/no-cache (+ 39 2)"))))

  (testing "handling binding forms i.e. def, defn"
    ;; ensure "some-var" is a variable in whatever namespace we're running in
    (intern (create-ns 'my-test-ns) 'some-var 0)
    (testing "the variable is properly defined"
      (is (match? [map?
                   {:type :code,
                    :result {:nextjournal/value {::clerk/var-from-def (var my-test-ns/some-var)}}}]
                  (clerk/eval-string "(ns ^:nextjournal.clerk/no-cache my-test-ns) (def some-var 99)")))
      (is (= 99 @(var my-test-ns/some-var))))))
