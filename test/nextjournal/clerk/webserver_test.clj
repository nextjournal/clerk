(ns nextjournal.clerk.webserver-test
  (:require [clojure.test :refer :all]
            [nextjournal.clerk.eval :as eval]
            [nextjournal.clerk.view :as view]
            [nextjournal.clerk.viewer :as viewer]
            [nextjournal.clerk.webserver :as webserver]))

(deftest serve-blob
  (testing "lazy loading of simple range"
    (let [doc (eval/eval-string "(range 100)")
          {:nextjournal/keys [presented fetch-opts]} (-> doc view/doc->viewer :nextjournal/value :blocks second :nextjournal/value)
          {:nextjournal/keys [value]} presented
          {elision-viewer :nextjournal/viewer elision-fetch-opts :nextjournal/value} (peek value)
          {:keys [body]} (webserver/serve-blob doc (merge fetch-opts {:fetch-opts elision-fetch-opts}))]
      (is (= :elision (:name elision-viewer)))
      (is body)
      (is (= (-> body webserver/read-msg :nextjournal/value first :nextjournal/value) 20)))))

(deftest extract-viewer-evals
  (testing "doesn't throw on sorted-map"
    (is (= #{} (-> (viewer/->edn '(into (sorted-map)
                                        {"A" ["A" "Aani" "Aaron"]
                                         "B" ["B" "Baal" "Baalath"]}))
                   eval/eval-string
                   webserver/extract-viewer-evals)))))
