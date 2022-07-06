(ns nextjournal.clerk.webserver-test
  (:require [clojure.test :refer :all]
            [nextjournal.clerk.eval :as eval]
            [nextjournal.clerk.view :as view]
            [nextjournal.clerk.webserver :as webserver]))

(defn read-result [s]
  (binding [*data-readers* {'viewer-fn identity}]
    (read-string s)))

(deftest serve-blob
  (testing "lazy loading of simple range"
    (let [doc (eval/eval-string "(range 100)")
          {:nextjournal/keys [edn fetch-opts]} (-> doc view/doc->viewer :nextjournal/value :blocks second second :nextjournal/value)
          {:nextjournal/keys [value]} (read-result edn)
          {elision-viewer :nextjournal/viewer elision-fetch-opts :nextjournal/value} (peek value)
          {:keys [body]} (webserver/serve-blob doc (merge fetch-opts {:fetch-opts elision-fetch-opts}))]
      (is (= :elision (:name elision-viewer)))
      (is body)
      (is (= (-> body read-result :nextjournal/value first :nextjournal/value) 20)))))
