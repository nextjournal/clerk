(ns nextjournal.clerk.eval-test
  (:require [babashka.fs :as fs]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [matcher-combinators.test :refer [match?]]
            [nextjournal.clerk :as clerk]
            [nextjournal.clerk.eval :as eval]
            [nextjournal.clerk.parser :as parser]
            [nextjournal.clerk.utils :as u]
            [nextjournal.clerk.view :as view]
            [nextjournal.clerk.viewer :as viewer]
            [nextjournal.clerk.webserver :as webserver]))

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

  (testing "does not error on lazy seq that integer overflows on freezable check"
    (eval/eval-string "(def fib (lazy-cat [0 1] (map + fib (rest fib))))"))

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
  (testing "side-effecting expression returning nil gets cached in memory"
    (let [code "(ns my-random-test-ns-2) (do (clojure.lang.Var/intern (the-ns 'my-random-test-ns-2) 'ruuid (java.util.UUID/randomUUID)) nil)"
          {:keys [blob->result]} (eval/eval-string code)]
      (is (= @(resolve 'my-random-test-ns-2/ruuid)
             (do (eval/eval-string blob->result code)
                 @(resolve 'my-random-test-ns-2/ruuid))))))

  (testing "var gets cached in cas"
    (let [code-str (format "(ns nextjournal.clerk.eval-test-%s) (def my-uuid (java.util.UUID/randomUUID))" (rand-int 100000))
          get-uuid #(-> % :blocks peek :result :nextjournal/value :nextjournal.clerk/var-from-def deref)]
      (is (= (get-uuid (eval/eval-string code-str))
             (get-uuid (eval/eval-string code-str))))))

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
    (is (match? {:blocks [{:result {:nextjournal/viewer `viewer/html}}]}
                (eval/eval-string "^{:nextjournal.clerk/viewer 'nextjournal.clerk.viewer/html} (def markup [:h1 \"hi\"])"))))

  (testing "var result that's not from a def should stay untouched"
    (is (match? {:blocks [{:result {:nextjournal/value {:nextjournal.clerk/var-from-def var?}}}
                          {:result {:nextjournal/value var?}}]}
                (eval/eval-string "(def foo :bar) (var foo)"))))

  (testing "definitions occurring in side effects from macro expansions should not end up wrapped in var-from-def maps as the cell result"
    (is (= :my-value
           (-> (eval/eval-string "(ns nextjournal.clerk.eval-test.def-side-effects {:nextjournal.clerk/no-cache true})
(defmacro define [name val] `(do (def ~name ~val) ~val))
(define my-value :my-value)") :blocks peek :result :nextjournal/value))))

  (testing "can handle unbounded sequences"
    (is (match? {:blocks [{:result {:nextjournal/value seq?}}]}
                (eval/eval-string "(range)")))
    (is (match? {:blocks [{:result {:nextjournal/value {:a seq?}}}]}
                (eval/eval-string "{:a (range)}"))))

  (testing "can handle failing hash computation for deref-dep"
    (eval/eval-string "(ns test-deref-hash (:require [nextjournal.clerk :as clerk])) (defonce !state (atom [(clerk/md \"_hi_\")])) @!state"))

  (testing "won't eval forward declarations"
    (is (:error
         (eval/eval-string "(ns test-forward-declarations {:nextjournal.clerk/no-cache true})
(declare delayed-def)
(inc delayed-def)
(def delayed-def 123)")))))

(defn eval+extract-doc-blocks [code-str]
  (-> code-str
      eval/eval-string
      view/doc->viewer
      :nextjournal/value
      :blocks
      (->> (mapcat :nextjournal/value))))

(deftest eval-string+doc->viewer
  (testing "assigns correct width from viewer function opts"
    (is (match? [{:nextjournal/width :wide}
                 {:nextjournal/width :full}]
                (->> "(ns clerk-test-width {:nextjournal.clerk/visibility {:code :hide}}
  (:require [nextjournal.clerk :as clerk]))


(clerk/html {::clerk/width :wide} [:div.bg-red-200 [:h1 \"Wide Hiccup\"]])

(clerk/table {::clerk/width :full} {:a [1] :b [2] :c [3]})"
                     eval+extract-doc-blocks
                     (mapv #(select-keys % [:nextjournal/width]))))))

  (testing "assigns the correct width from form meta"
    (is (match? [{:nextjournal/width :full}
                 {:nextjournal/width :wide}]
                (->> "(ns clerk-test-width {:nextjournal.clerk/visibility {:code :hide}})

^{:nextjournal.clerk/viewer 'nextjournal.clerk.viewer/table-viewer :nextjournal.clerk/width :full}
(def dataset
  [[1 2] [3 4]])

^{:nextjournal.clerk/viewer 'nextjournal.clerk.viewer/html-viewer :nextjournal.clerk/width :wide}
[:div.bg-red-200 [:h1 \"Wide Hiccup\"]]
"
                     eval+extract-doc-blocks
                     (mapv #(select-keys % [:nextjournal/width]))))))

  (testing "can handle uncounted sequences"
    (is (match? [{:nextjournal/viewer {:name `viewer/code-block-viewer}
                  :nextjournal/value "(range)"}
                 {:nextjournal/value {:nextjournal/fetch-opts {:blob-id string?}
                                      :nextjournal/hash string?}}]
                (eval+extract-doc-blocks "(range)"))))

  (testing "Skipping pagination for strings"
    (is (= "012345678910111213141516171819202122232425262728293031323334353637383940414243444546474849"
           (-> (eval+extract-doc-blocks "^{:nextjournal.clerk/page-size nil} (apply str (range 50))")
               second :nextjournal/value :nextjournal/presented :nextjournal/value))))

  (testing "assigns folded visibility"
    (is (match? [{:nextjournal/viewer {:name `viewer/folded-code-block-viewer}
                  :nextjournal/value "{:some :map}"}
                 {:nextjournal/value {:nextjournal/fetch-opts {:blob-id string?}
                                      :nextjournal/hash string?}}]
                (eval+extract-doc-blocks "^{:nextjournal.clerk/visibility :fold}{:some :map}"))))

  (testing "handles sorted map"
    (view/doc->viewer (eval/eval-string (viewer/->edn '(into (sorted-map)
                                                             {"A" ["A" "Aani" "Aaron"]
                                                              "B" ["B" "Baal" "Baalath"]})))))

  (testing "hides the result"
    (is (= []
           (eval+extract-doc-blocks "^{:nextjournal.clerk/visibility {:code :hide :result :hide}}
 {:some :map}")))))

(deftest eval-analyzed-doc
  (testing "should fail when var is only present at runtime but not in file"
    (intern (create-ns 'missing-var) 'foo :bar)
    (is (thrown-with-msg? Exception
                          #"is being referenced, but Clerk can't find it in the namespace's source code"
                          (eval/eval-string "(ns missing-var) foo"))))

  (testing "should not fail on var present at runtime if there's no ns form"
    (intern (create-ns 'existing-var) 'foo :bar)
    (is (eval/eval-string "(in-ns 'existing-var) foo"))))

(deftest eval+cache!
  (testing "edge case where some form is not evaluated"
    (is (eval+extract-doc-blocks "(ns repro-crash-when-not-all-forms-are-evaluated
  {:nextjournal.clerk/no-cache true})

(do
  (def b 2)
  (declare a))

(def a 1)

(+ a b)
"))))

(clerk/defcached my-expansive-thing
  (do (Thread/sleep 1 #_10000) 42))

(deftest defcached
  (is (= 42 my-expansive-thing)))

(deftest with-cache
  (is (= 42 (clerk/with-cache
              (do (Thread/sleep 1 #_10000) 42)))))

(deftest cacheable-value?-test
  (testing "finite sequence is cacheable"
    (is (eval/cachable? (vec (range 100)))))
  (testing "infinite sequences can't be cached"
    (is (not (eval/cachable? (range))))
    (is (not (eval/cachable? (map inc (range))))))
  (testing "class is not cachable"
    (is (not (eval/cachable? java.lang.String)))
    (is (not (eval/cachable? {:foo java.lang.String}))))
  (u/when-not-bb
   (testing "image is cachable"
     (is (eval/cachable? (javax.imageio.ImageIO/read (io/file "trees.png")))))))

(deftest show!-test
  (testing "in-memory cache is preserved when exception is thrown (#549)"
    (let [code "{:f inc :n (rand-int 100000)}"
          get-result #(:blob->result @webserver/!doc)]
      (clerk/show! (java.io.StringReader. code))
      (let [result-first-run (get-result)]
        (try (clerk/show! (java.io.StringReader. (str code " (throw (ex-info \"boom\" {}))")))
             (catch Exception _ nil))
        (clerk/show! (java.io.StringReader. code))
        (is (= result-first-run (get-result)))))))

(deftest present!-test
  (testing "presented value is returned"
    (is (= {:path [0]
            :nextjournal/value 42
            :nextjournal/render-opts {:id "nextjournal.clerk.presenter/presented-result"}}
           (select-keys (clerk/present! 42) [:path :nextjournal/render-opts :nextjournal/value]))))
  (testing "present"
    (is (= 42 (:nextjournal/value (clerk/present 42))))))

(deftest file-var-metadata-test
  (testing "show with file string arg"
    (clerk/show! "test/nextjournal/clerk/fixtures/hello.clj")
    (is (fs/exists? (:file (meta (resolve 'nextjournal.clerk.fixtures.hello/answer))))))
  (testing "show with ns arg"
    (clerk/show! 'nextjournal.clerk.fixtures.hello)
    (is (fs/exists? (:file (meta (resolve 'nextjournal.clerk.fixtures.hello/answer)))))))
