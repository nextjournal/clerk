(ns nextjournal.clerk.webserver-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]
            [nextjournal.clerk.eval :as eval]
            [nextjournal.clerk.viewer :as viewer]
            [nextjournal.clerk.view :as view]
            [nextjournal.clerk.webserver :as webserver]))

(deftest ->file-or-ns
  (is (= 'nextjournal.clerk.tap (webserver/->file-or-ns "'nextjournal.clerk.tap")))
  (is (= "notebooks/rule_30.clj" (webserver/->file-or-ns "notebooks/rule_30.clj"))))

(defn stream-equal? [s1 s2]
  (let [x1 (.read s1) x2 (.read s2)]
    (if (and (not= -1 x1) (not= -1 x2))
      (if (= x1 x2) (recur s1 s2) false)
      (= x1 x2))))

(deftest serve-blob
  (testing "lazy loading of simple range"
    (let [doc (let [doc (eval/eval-string "(range 100)")]
                (with-meta doc (view/doc->viewer doc)))
          {:nextjournal/keys [presented fetch-opts]} (-> doc meta :nextjournal/value :blocks first :nextjournal/value second :nextjournal/value)
          {:nextjournal/keys [value]} presented
          {elision-viewer :nextjournal/viewer elision-fetch-opts :nextjournal/value} (peek value)
          {:keys [body]} (webserver/serve-blob doc (merge fetch-opts {:fetch-opts elision-fetch-opts}))]
      (is (= `nextjournal.clerk.viewer/elision-viewer (:name elision-viewer)))
      (is body)
      (is (= (-> body webserver/read-msg :nextjournal/value first :nextjournal/value) 20))))

  (testing "lazy loading of images"
    (let [doc (let [doc (eval/eval-string "(ns nextjournal.clerk.webserver-test.lazy-load-image
  {:nextjournal.clerk/no-cache true}
  (:require [nextjournal.clerk :as clerk]))

(clerk/image \"trees.png\")
")]
                (with-meta doc (view/doc->viewer doc)))
          {:nextjournal/keys [presented fetch-opts]} (-> doc meta :nextjournal/value :blocks second :nextjournal/value second :nextjournal/value)
          {:nextjournal/keys [value]} presented]
      (is (= "image/png" (:nextjournal/content-type presented)))
      (is (= (:blob-id fetch-opts) (:blob-id (:nextjournal/value presented))))
      ;; the presented image has been processed to only contain their blob-id in the value
      ;; serve blob resolve their original contents via the elision mechanism
      (let [response-body (:body (webserver/serve-blob doc (merge fetch-opts {:fetch-opts value})))]
        (is (bytes? response-body))
        (is (= (seq (viewer/buffered-image->bytes (viewer/read-image "trees.png")))
               (seq response-body)))))

    (testing "lazy loading of elided images"
      (let [doc (let [doc (eval/eval-string "(ns nextjournal.clerk.webserver-test.lazy-load-image
  {:nextjournal.clerk/no-cache true}
  (:require [nextjournal.clerk :as clerk]))

(concat (range 20)
        (list (clerk/image \"trees.png\")))")]
                  (with-meta doc (view/doc->viewer doc)))
            {:nextjournal/keys [presented fetch-opts]} (-> doc meta :nextjournal/value :blocks second :nextjournal/value second :nextjournal/value)
            {:nextjournal/keys [value]} presented
            {elision-fetch-opts :nextjournal/value v :nextjournal/viewer} (peek value)
            ]
        (is (= (:name viewer/elision-viewer) (:name v)))
        (let [{:keys [body]} (webserver/serve-blob doc (merge fetch-opts {:fetch-opts elision-fetch-opts}))
              {expanded-value :nextjournal/value}
              (binding [*data-readers* (assoc viewer/data-readers 'object (fn [_v] [:__object__]))]
                (read-string body))]
          (is (= "image/png" (-> expanded-value first :nextjournal/content-type)))
          ;; blobs contained inside fetched elisions are being processed
          (is (= {:blob-id (:blob-id fetch-opts) :path [1 20]}
                 (-> expanded-value first :nextjournal/value))))))))

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
