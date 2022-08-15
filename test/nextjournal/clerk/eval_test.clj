(ns nextjournal.clerk.eval-test
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [matcher-combinators.test :refer [match?]]
            [nextjournal.clerk.eval :as eval]
            [nextjournal.clerk.parser :as parser]
            [nextjournal.clerk.view :as view]
            [nextjournal.clerk.viewer :as viewer]))

(deftest eval-string
  (testing "hello 42"
    (is (match? {:blocks [{:type :code,
                           :result {:nextjournal/value 42}}]}
                (eval/eval-string "(+ 39 3)")))
    (is (match? {:blocks [{:type :code,
                           :result {:nextjournal/value 41}}]}
                (eval/eval-string "(+ 39 2)")))
    (is (match? {:blocks [{:type :code,
                           :result {:nextjournal/value 41}}]}
                (eval/eval-string "^:nextjournal.clerk/no-cache (+ 39 2)"))))

  (testing "all values gets blob id"
    (let [{:keys [blocks blob->result]} (eval/eval-string "(ns user) (inc 41) 42")]
      (is (match? [map?
                   {:result {:nextjournal/value 42}}
                   {:result {:nextjournal/value 42}}]
                  blocks))
      (is (= 0 (count (into [] (comp (map key) (filter nil?)) blob->result))))))

  (testing "the 'previous results' cache takes first precedence"
    (let [doc (parser/parse-clojure-string "(inc 41)")
          {:keys [blob->result]} (eval/eval-doc doc)
          [blob-id {v :nextjournal/value}] (first blob->result)]
      (is (= v 42))
      (is (match? {:blocks [{:result {:nextjournal/value -4}}]}
                  (eval/eval-doc {blob-id {:nextjournal/value -4}} doc)))))

  (testing "does not hang on java.nio.Path result (issue #199)"
    (eval/eval-string "(.toPath (clojure.java.io/file \"something\"))")
    (eval/eval-string "[(.toPath (clojure.java.io/file \"something\"))]"))

  (testing "handling binding forms i.e. def, defn"
    ;; ensure "some-var" is a variable in whatever namespace we're running in
    (testing "the variable is properly defined"
      (let [{:keys [blocks]} (eval/eval-string "(ns ^:nextjournal.clerk/no-cache my-test-ns) (def some-var 99)")]
        (is (match? [map?
                     {:type :code,
                      :result {:nextjournal/value {:nextjournal.clerk/var-from-def var?}}}]
                    blocks))
        (is (= 99 @(find-var 'my-test-ns/some-var))))))

  (testing "random expression gets cached"
    (is (= (eval/eval-string "(ns my-random-test-ns-1) (rand-int 1000000)")
           (eval/eval-string "(ns my-random-test-ns-1) (rand-int 1000000)"))))

  (testing "random expression that cannot be serialized in nippy gets cached in memory"
    (let [{:as result :keys [blob->result]} (eval/eval-string "(ns my-random-test-ns-2) {inc (java.util.UUID/randomUUID)}")]
      (is (= result
             (eval/eval-string blob->result "(ns my-random-test-ns-2) {inc (java.util.UUID/randomUUID)}")))))

  (testing "random expression doesn't get cached with no-cache"
    (is (not= (eval/eval-string "(ns ^:nextjournal.clerk/no-cache my-random-test-ns-3) (java.util.UUID/randomUUID)")
              (eval/eval-string "(ns ^:nextjournal.clerk/no-cache my-random-test-ns-3) (java.util.UUID/randomUUID)"))))

  (testing "random dependent expression doesn't get cached with no-cache"
    (let [code "(ns my-random-test-ns-4) (def ^:nextjournal.clerk/no-cache my-uuid (java.util.UUID/randomUUID)) (str my-uuid)"
          eval+get-last-block-val (fn [] (-> code eval/eval-string :blocks peek :result :nextjournal/value))]
      (is (not= (eval+get-last-block-val)
                (eval+get-last-block-val)))))

  (testing "random expression that cannot be frozen with nippy gets cached via in-memory cache"
    (let [code "(ns my-random-test-ns-5) {:my-fn inc :my-uuid (java.util.UUID/randomUUID)}"
          result (eval/eval-string code)
          result' (eval/eval-string (:blob->result result) code)
          extract-my-uuid #(-> % :blocks last :result :nextjournal/value :my-uuid)]
      (is (= (extract-my-uuid result)
             (extract-my-uuid result')))))

  (testing "do blocks with defs are not cached on disk"
    (let [code "(ns my-test-ns-7) (do (defonce counter (atom 0)) (swap! counter inc))"
          extract-value #(-> % :blocks last :result :nextjournal/value)]
      (is (= 1 (extract-value (eval/eval-string code))))
      (is (= 2 (extract-value (eval/eval-string code))))
      (ns-unmap 'my-test-ns-7 'counter)))

  (testing "old values are cleared from in-memory cache"
    (let [{:keys [blob->result]} (eval/eval-string "(ns my-random-test-ns-6) ^:nextjournal.clerk/no-cache {inc (java.util.UUID/randomUUID)}")]
      (is (= 2 (count (:blob->result (eval/eval-string blob->result "(ns my-random-test-ns-6) {inc (java.util.UUID/randomUUID)}")))))))

  (testing "defonce returns correct result on subsequent evals (when defonce would eval to nil)"
    (eval/eval-string "(ns ^:nextjournal.clerk/no-cache my-defonce-test-ns) (defonce state (atom {}))")
    (is (match? {:blocks [map? {:result {:nextjournal/value {:nextjournal.clerk/var-from-def var?}}}]}
                (eval/eval-string "(ns ^:nextjournal.clerk/no-cache my-defonce-test-ns) (defonce state (atom {}))"))))

  (testing "assigning viewers from form meta"
    (is (match? {:blocks [{:result {:nextjournal/viewer fn?}}]}
                (eval/eval-string "^{:nextjournal.clerk/viewer nextjournal.clerk/table} (def markup [:h1 \"hi\"])")))
    (is (match? {:blocks [{:result {:nextjournal/viewer :html}}]}
                (eval/eval-string "^{:nextjournal.clerk/viewer :html} (def markup [:h1 \"hi\"])"))))

  (testing "var result that's not from a def should stay untouched"
    (is (match? {:blocks [{:result {:nextjournal/value {:nextjournal.clerk/var-from-def var?}}}
                          {:result {:nextjournal/value var?}}]}
                (eval/eval-string "(def foo :bar) (var foo)"))))

  (testing "can handle unbounded sequences"
    (is (match? {:blocks [{:result {:nextjournal/value seq?}}]}
                (eval/eval-string "(range)")))
    (is (match? {:blocks [{:result {:nextjournal/value {:a seq?}}}]}
                (eval/eval-string "{:a (range)}")))))

(deftest eval-string+doc->viewer
  (testing "assigns correct width from viewer function opts"
    (is (match? [{:nextjournal/width :wide}
                 {:nextjournal/width :full}]
                (-> "^{:nextjournal.clerk/visibility :hide} (ns clerk-test-width
  (:require [nextjournal.clerk :as clerk]))

(clerk/html {::clerk/width :wide} [:div.bg-red-200 [:h1 \"Wide Hiccup\"]])

(clerk/table {::clerk/width :full} {:a [1] :b [2] :c [3]})"
                    eval/eval-string
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
                    eval/eval-string
                    view/doc->viewer
                    :nextjournal/value
                    :blocks))))

  (testing "can handle uncounted sequences"
    (is (match? [{:nextjournal/viewer {:name :code}
                  :nextjournal/value "(range)"}
                 {:nextjournal/viewer {:name :clerk/result}
                  :nextjournal/value {:nextjournal/edn string?
                                      :nextjournal/fetch-opts {:blob-id string?}
                                      :nextjournal/hash string?}}]
                (-> "(range)"
                    eval/eval-string
                    view/doc->viewer
                    :nextjournal/value
                    :blocks))))

  (testing "assigns folded visibility"
    (is (match? [{:nextjournal/viewer {:name :code-folded}
                  :nextjournal/value "^{:nextjournal.clerk/visibility :fold}{:some :map}"}
                 {:nextjournal/viewer {:name :clerk/result}
                  :nextjournal/value {:nextjournal/edn string?
                                      :nextjournal/fetch-opts {:blob-id string?}
                                      :nextjournal/hash string?}}]
                (-> "^{:nextjournal.clerk/visibility :fold}{:some :map}"
                    eval/eval-string
                    view/doc->viewer
                    :nextjournal/value
                    :blocks))))

  (testing "hides the result"
    (is (= []
           (-> "^{:nextjournal.clerk/viewer :hide-result
  :nextjournal.clerk/visibility :hide}
 {:some :map}
^{:nextjournal.clerk/viewer nextjournal.clerk/hide-result
  :nextjournal.clerk/visibility :hide}
 {:another :map}
^{:nextjournal.clerk/viewer nextjournal.clerk.viewer/hide-result-viewer
  :nextjournal.clerk/visibility :hide}
 {:a-third :map}"
               eval/eval-string
               view/doc->viewer
               :nextjournal/value
               :blocks)))))
