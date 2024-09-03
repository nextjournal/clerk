(ns nextjournal.clerk.require-cljs-test
  (:require [clojure.test :as t :refer [deftest is]]
            [nextjournal.clerk :as clerk]))

(deftest throw-on-missing-cljs-source-test
  (is (thrown-with-msg? Exception #"Could not find source for CLJS namespace: not-existing"
                        (clerk/show! 'nextjournal.clerk.fixtures.require-cljs-with-missing-cljs-file)))
  (is (thrown-with-msg? Exception #"Could not find source for CLJS namespace: non-existing-namespace"
                        (clerk/show! 'nextjournal.clerk.fixtures.require-cljs-with-transitive-missing-cljs-file))))

;;;; Scratch

(comment
  (t/run-tests)
  )
