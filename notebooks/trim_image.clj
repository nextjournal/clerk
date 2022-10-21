;; # ✂️ Trim Images Testbed
(ns trim-image
  {:nextjournal.clerk/no-cache true
   :nextjournal.clerk/visibility {:code :hide}}
  (:require [nextjournal.clerk :as clerk]
            [cards-macro :as c]))

(c/card
 (v/with-viewer
   #(v/html
     [:div#images
      [:input.text-xs.font-sans
       {:type "file"
        :id "file-input"
        :on-change (fn [event]
                     (let [img (js/document.getElementById "original")]
                       (j/assoc! img :src (js/URL.createObjectURL (j/get-in event [:target :files 0])))))}]
      [:div.mt-12
       [:h4 "Original"]
       [:img.border.border-green-500 {:id "original"
                                      :on-load (fn [event]
                                                 (let [result (js/document.getElementById "result")
                                                       result-padding (js/document.getElementById "result-padding")
                                                       img (.-target event)]
                                                   (j/assoc! result :innerHTML "")
                                                   (.appendChild result (js/nextjournal.clerk.trim_image.trim_image img))
                                                   (j/assoc! result-padding :innerHTML "")
                                                   (.appendChild result-padding (js/nextjournal.clerk.trim_image.trim_image img {:padding 20}))))}]
       [:h4 "Trimmed"]
       [:div.flex
        [:div#result.border.border-red-500]]
       [:h4 "Trimmed with padding"]
       [:div.flex
        [:div#result-padding.border.border-red-500]]]])
   nil))
