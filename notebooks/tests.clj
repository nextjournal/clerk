;; # 🧪 Clojure Tests
(ns tests
  {:nextjournal.clerk/no-cache true}
  (:require [nextjournal.clerk :as clerk]
            [clojure.test :refer :all :as t]
            [nextjournal.clerk.viewer :as v]
            [clojure.string :as str]))

{::clerk/visibility {:code :hide :result :hide}}
(defn applying-fixtures [tx-fn]
  (fn [wrapped-value]
    (let [doc (v/->value wrapped-value)
          once-ffn (-> doc :ns meta ::t/once-fixtures clojure.test/join-fixtures)
          each-ffn (-> doc :ns meta ::t/each-fixtures clojure.test/join-fixtures)
          !wv (atom (update wrapped-value :nextjournal/value assoc :each-fixtures each-ffn))
          report-counters (ref {})]
      (binding [clojure.test/*report-counters* report-counters]
        (once-ffn (fn []
                    (println "running all tests")
                    (swap! !wv tx-fn))))                    ;; TODO: comp with tx-fn

      (update @!wv
              :nextjournal/value
              assoc
              :toc-visibility :show
              :toc {:children (into []
                                    (map (fn [[k v]] {:content [{:text (str (str/capitalize (name k)) ": " v)}]}))
                                    @report-counters)}))))

(defn guard [p v] (when (p v) v))
(defn run-test-var+report [{:as wv {:as cell :keys [result]} :nextjournal/value opts :nextjournal/opts}]
  (if-some [test-var (guard (comp :test meta) (-> result v/->value ::clerk/var-from-def))]
    (let [report-str-writer (java.io.StringWriter.)]
      ;; test effect
      (binding [clojure.test/*test-out* report-str-writer]
        ((:each-fixtures opts)
         (fn [] (clojure.test/test-var test-var))))
      (assoc-in wv
                [:nextjournal/value :result :nextjournal/value]
                (v/html (if-some [failure-report (not-empty (str report-str-writer))]
                          [:pre.border-8.border-red-300 failure-report]
                          [:h3 "✅ " [:em.text-green-600 "passed!"]]))))
    wv))

(def test-runner-viewer (update v/notebook-viewer :transform-fn applying-fixtures))
(def test-result-viewer (update v/result-block-viewer :transform-fn comp run-test-var+report))

{::clerk/visibility {:code :show :result :hide}}
;; We're overriding the notebook viewer to actually run test vars after evaluation
(clerk/add-viewers! [test-result-viewer
                     test-runner-viewer])

{::clerk/visibility {:code :show :result :show}}

;; This is a notebook with usual test setup and assertions
(def exhibits (atom []))

(defn populate-museum [t] (swap! exhibits conj "🦖" "🦕") (t) (swap! exhibits (comp pop pop)))

(use-fixtures :once populate-museum)

(def a-stegosaurus? #{'a-stego})
(def a-trex? #{"🦖"})

(deftest any-exhibits-test
  (testing "When I visit a museum, I expect to see something"
    (is (not-empty @exhibits))))

(deftest what-for-exhibits-test
  (testing "When I visit a museum"

    (testing "I should be seeing a T-Rex"
      (is (a-trex? (first @exhibits))))

    (testing "I should be seeing a Stegosaurus"
      (is (a-stegosaurus? (second @exhibits))))))

(deftest a-lost-world
  (testing "When I visit a museum, they shouldn't be alive"
    (throw (Error. "Roar!"))))
