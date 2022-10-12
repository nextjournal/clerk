(ns nextjournal.clerk.view-test
  (:require [clojure.test :refer :all]
            [nextjournal.clerk.view :as view]
            [nextjournal.clerk.viewer :as viewer]
            [nextjournal.clerk.eval :as eval]
            [clojure.edn :as edn]
            [clojure.walk :as w]
            [clojure.string :as str]))

(def has-content-type?
  (every-pred viewer/wrapped-value?
              :nextjournal/content-type))

(defn presented-images [presented-doc]
  (let [!matches (atom nil)]
    (w/postwalk #(do (when (has-content-type? %)
                       (swap! !matches conj %)) %)
                presented-doc)
    @!matches))

(defn read-results [presented-doc]
  (update-in presented-doc [:nextjournal/value :blocks]
             (fn [blocks]
               (mapv (fn [b]
                       (cond-> b
                         (get-in b [:nextjournal/value :nextjournal/edn])
                         (update-in [:nextjournal/value :nextjournal/edn]
                                    #(edn/read-string {:default (constantly nil)} %)))) blocks))))

(deftest doc->viewer
  (testing "build options are propagated to blob processing"
    (let [test-doc (eval/eval-string "
;; # Test Doc

(let [img (java.awt.image.BufferedImage. 20 20 1)]
  (nextjournal.clerk/row img img))
")]

      (is (true? (->> (view/doc->viewer {} test-doc)
                      read-results presented-images (map viewer/->value)
                      (every? (comp #(= #{:blob-id :path} %) set keys)))))

      (is (true? (->> (view/doc->viewer {:inline-results? true :bundle? false} test-doc)
                      read-results presented-images (map viewer/->value)
                      (every? (every-pred string?
                                          (partial re-matches #"_data.+\.png"))))))

      (is (true? (->> (view/doc->viewer {:inline-results? true :bundle? true} test-doc)
                      read-results presented-images (map viewer/->value)
                      (every? (every-pred string?
                                          #(str/starts-with? % "data:image/png;base64")))))))))
