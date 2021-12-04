(ns nextjournal.clerk-test
  (:require [nextjournal.clerk :as c]
            [clojure.test :as t]))

(t/deftest supported-filenames
  (t/is (= true (c/supported-file? "xyz/name.md")))
  (t/is (= true (c/supported-file? "xyz/name.clj")))
  (t/is (= true (c/supported-file? "name.clj")))
  (t/is (= true (c/supported-file? "xyz/name.cljc")))
  (t/is (= false (c/supported-file? "xyz/name.any")))
  (t/is (= false (c/supported-file? "xyz/.#name.cljc")))
  (t/is (= true (c/supported-file? "xyz/abc.#name.cljc")))
  )
