;; # Binary Viewer
(ns viewers.binary
  (:require [nextjournal.clerk :as clerk]
            [clojure.string :as str]
            [babashka.fs :as fs]))

(defn binary->hex [xs]
  (let [bytes-per-row 16
        bytes-per-cell 2]
    (clerk/html [:pre (->> (javax.xml.bind.DatatypeConverter/printHexBinary xs)
                           (str/lower-case)
                           (partition bytes-per-cell)
                           (mapv str/join)
                           (partition bytes-per-row)
                           (mapv #(str (str/join " " %) " | " (str/join (mapv (fn [hex] (char (Integer/parseInt hex 16))) %))))
                           (into [] (map-indexed (fn [idx s]
                                                   (format "%08x %s" (* bytes-per-row idx) s))))
                           (str/join "\n"))])))



(def binary-hex-viewer
  {:pred bytes?
   :transform-fn (clerk/update-val binary->hex)})


(clerk/add-viewers! [binary-hex-viewer])

(def s "hello world what 💙 is happening? Why am I really here? 💙")

(.getBytes s)

(fs/read-all-bytes (fs/file ".clerk/cache/8Vv691kYDWCdh5fuFnTjx6f66CAUjdh4htqSHJ2w2YzNd7yuV68zhvgrMNJ4oxeKj1JTfMkftfw55FEaMZWkBiYTXg"))
