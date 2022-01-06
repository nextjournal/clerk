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
 @(find-var (symbol (str *ns*) var-name)))

(deftest read+eval-cached
  (testing "basic eval'ing will give a result with a hash"
    (let [first-run (clerk/read+eval-cached {} {} #{:show} "{:x (inc 10)}")]
      (is (match? {:nextjournal/value            {:x 11}
                   :nextjournal.clerk/visibility #{:show}
                   :nextjournal/blob-id          any?}
                  first-run))
      (testing "the 'previous results' cache takes first precedence"
        (is (match? {:nextjournal/value :hacked}
                    (clerk/read+eval-cached {(:nextjournal/blob-id first-run) :hacked}
                                            {}
                                            #{:show}
                                            "{:x (inc 10)}"))))))

  (testing "eval'ing stores results in a cache"
    ;; ensure "a-var" is a variable in whatever namespace we're running in
    (intern *ns* 'a-var 0)

    (is (match? {:nextjournal/value 1}
                (clerk/read+eval-cached {} {} #{:show} "(inc a-var)")))

    ;; sneakily change the var under the hood
    (with-redefs [hashing/no-cache? (constantly true)]
      (clerk/read+eval-cached {} {} #{:show} "(alter-var-root #'a-var inc)"))

    (testing "the expression is cached and we don't see the change to `a-var`"
      (is (match? {:nextjournal/value 1}
                  (clerk/read+eval-cached {} {} #{:show} "(inc a-var)"))))

    (testing "with caching off, we get the freshly altered result"
      (with-redefs [hashing/no-cache? (constantly true)]
        (is (match? {:nextjournal/value 2}
                    (clerk/read+eval-cached {} {} #{:show} "(inc a-var)"))))))

  (testing "handling binding forms i.e. def, defn"
    ;; ensure "some-var" is a variable in whatever namespace we're running in
    (intern *ns* 'some-var 0)

    (let [{:nextjournal/keys [blob-id]} (clerk/read+eval-cached {} {} #{:show} "(def some-var 99)")]
      (testing "the variable is properly defined"
        (is (= 99 (lookup-var-in-*ns* "some-var"))))

      (testing ".. even if we load it from the cache after a fresh start"
        (is (match? {:nextjournal/value (symbol (str *ns*) "some-var")}
                    (clerk/read+eval-cached {blob-id -100} {} #{:show} "(def some-var 99)")))
        (is (= -100 (lookup-var-in-*ns* "some-var")))))))
