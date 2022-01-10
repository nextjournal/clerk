^:nextjournal.clerk/no-cache
(ns test-driven
  (:require [clojure.test :refer [deftest is testing] :as t]
            [nextjournal.clerk :as clerk]))

;; 🧪 testing within a notebook

;; viewer that runs tests when it encounters a test-var
(defn- test-var? [x]
  (not (nil? (when-let [v (get x :nextjournal.clerk/var-from-def)]
               (println v)
               (:test (meta v))))))

(defn with-test-out->str [func]
  (let [s (new java.io.StringWriter)]
    (binding [t/*test-out* s]
      (func))
    (str s)))

(defn test-runner-viewer [v]
  (with-test-out->str #(t/run-test-var v)))


(clerk/set-viewers!
  [{:pred         test-var?
    :transform-fn #(-> % :nextjournal.clerk/var-from-def test-runner-viewer)
    :render-fn    #(v/html [:span.syntax-string.inspected-value %])}])

(deftest foo
  (is (= 1 2))
  (is (= 1 1)))

;; if we want to display `testing` results we'll need to wrap them somehow like
;; this:
(with-test-out->str
  #(testing
     (is (= [1] [2]))))

(comment
  (nextjournal.clerk/serve! {:watch-paths ["notebooks"]})
  (nextjournal.clerk/show! "notebooks/test_driven.clj")
  )
