(ns nextjournal.clerk.git-test
  (:require [clojure.test :refer [deftest is testing]]
            [nextjournal.clerk.git :as git]))

(deftest ->github-project
  (testing "works with .git suffix"
    (is (= "nextjournal/clerk"
           (git/->github-project "git@github.com:nextjournal/clerk.git"))))
  (testing "works without .git suffix"
    (is (= "nextjournal/clerk"
           (git/->github-project "git@github.com:nextjournal/clerk"))))
  (testing "works only for github"
    (is (nil? (git/->github-project "git@gitlab.com:other/host.git")))))

(deftest ->https-git-url
  (testing "works for https"
    (is (= "https://github.com/nextjournal/clerk"
           (git/->https-git-url "https://github.com/nextjournal/clerk.git")))
    (is (= "https://gitlab.com/other/host"
           (git/->https-git-url "https://gitlab.com/other/host.git"))))
  (testing "rewrites github ssh to https"
    (is (= "https://github.com/nextjournal/clerk"
           (git/->https-git-url "git@github.com:nextjournal/clerk.git")))))

