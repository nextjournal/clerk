(ns nextjournal.clerk.webserver-test
  (:require [clojure.test :refer [deftest is testing]]
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


#_#_#_#_
(def ^:dynamic *test-dyn-var* 0)

(var-set #'foo)


(binding [*test-dyn-var* 42]
  (def ^:dynamic *test-dyn-var* 33)
  (var-set #'*test-dyn-var* 33)
  *test-dyn-var*)


(binding [webserver/*session* :my-session]
  (let [evaluated-doc (eval/eval-string "(ns clerk.sessions-test (:require [nextjournal.clerk :as clerk]))
^{::clerk/sync true}
(defonce ^:dynamic !offset (atom 0))
(def ^:dynamic offset-dep-var @!offset)")]
    
    (is (= 0 @(resolve 'clerk.sessions-test/offset-dep-var)))))
