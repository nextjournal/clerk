;; # Binary Viewer
(ns viewers.binary
  (:require [nextjournal.clerk :as clerk]
            [clojure.string :as str]
            [babashka.fs :as fs]))

(defn binary->hex [xs]
  (let [bytes-per-row 16
        bytes-per-cell 2]
    (clerk/html
     [:div.font-mono.text-xs.leading-none
      [:div.pl-1
       (into [:div.flex.gap-2.text-slate-500.pl-1 {:class "ml-[80px]"}]
             (map (fn [i]
                    [:div
                     {:class "p-[3px] pb-[7px]"} (format "%02x" i)])
                  (range 0 bytes-per-row)))]
      [:div.flex
       (into [:div]
             (->> (javax.xml.bind.DatatypeConverter/printHexBinary xs)
                  (str/lower-case)
                  (partition bytes-per-cell)
                  (partition bytes-per-row)
                  (map-indexed (fn [i cells]
                                 [:div.flex.hover:bg-indigo-100
                                  [:div.mr-2.pl-1.text-slate-500 {:class "py-[3px] w-[80px]"} (format "%08x" (* bytes-per-row i))]
                                  (into [:div.flex.mr-2.gap-2.pr-2]
                                        (mapv (fn [cell]
                                                (into [:div.hover:bg-indigo-600.hover:text-white.transition-all.hover:scale-150.duration-150
                                                       {:class "p-[3px]"}]
                                                      cell))
                                              cells))
                                  [:div.px-1 {:class "py-[3px]"}
                                   (str/join (mapv (fn [cell] (char (Integer/parseInt (str/join cell) 16))) cells))]]))))]])))



(def binary-hex-viewer
  {:pred bytes?
   :transform-fn (clerk/update-val binary->hex)})


(clerk/add-viewers! [binary-hex-viewer])

(def s "hello world what 💙 is happening? Why am I really here? 💙")

(.getBytes s)

(fs/read-all-bytes (fs/file ".clerk/cache/8Vv691kYDWCdh5fuFnTjx6f66CAUjdh4htqSHJ2w2YzNd7yuV68zhvgrMNJ4oxeKj1JTfMkftfw55FEaMZWkBiYTXg"))
