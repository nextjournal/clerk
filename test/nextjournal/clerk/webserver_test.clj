(ns nextjournal.clerk.webserver-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]
            [nextjournal.clerk.eval :as eval]
            [nextjournal.clerk.view :as view]
            [nextjournal.clerk.webserver :as webserver]))

(deftest ->file-or-ns
  (is (= 'nextjournal.clerk.tap (webserver/->file-or-ns "'nextjournal.clerk.tap")))
  (is (= "notebooks/rule_30.clj" (webserver/->file-or-ns "notebooks/rule_30.clj"))))

(deftest serve-blob
  (testing "lazy loading of simple range"
    (let [doc (let [doc (eval/eval-string "(range 100)")]
                (with-meta doc (view/doc->viewer doc)))
          {:nextjournal/keys [presented fetch-opts]} (-> doc view/doc->viewer :nextjournal/value :blocks second :nextjournal/value)
          {:nextjournal/keys [value]} presented
          {elision-viewer :nextjournal/viewer elision-fetch-opts :nextjournal/value} (peek value)
          {:keys [body]} (webserver/serve-blob doc (merge fetch-opts {:fetch-opts elision-fetch-opts}))]
      (is (= `nextjournal.clerk.viewer/elision-viewer (:name elision-viewer)))
      (is body)
      (is (= (-> body webserver/read-msg :nextjournal/value first :nextjournal/value) 20)))))

(deftest serve-file-test
  (testing "serving a file resource"
    (is (= 200 (:status (webserver/serve-file "public/clerk_service_worker.js" "resources/public/clerk_service_worker.js")))
        (= {"Content-Type" "text/javascript"} (:headers (webserver/serve-file "public/clerk_service_worker.js" "resources/public/clerk_service_worker.js"))))))

(deftest serve-resource-test
  (testing "serving a file resource"
    (is (= 200 (:status (webserver/serve-resource (io/resource "public/clerk_service_worker.js"))))
        (= {"Content-Type" "text/javascript"} (:headers (webserver/serve-resource (io/resource "public/clerk_service_worker.js"))))))

  (testing "serving a resource from a jar"
    (is (= 200 (:status (webserver/serve-resource (io/resource "weavejester/dependency.cljc")))))))
