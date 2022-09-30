;; # ✂️ Trim Images Testbed
(ns trim-image
  {:nextjournal.clerk/no-cache true
   :nextjournal.clerk/visibility {:code :hide}}
  (:require [nextjournal.clerk :as clerk]
            [cards-macro :as c]))

(c/card
 (do
   (defn get-rgba [x y img-width img-data]
     (let [coord (* (+ (* img-width y) x) 4)]
       {:r (.at img-data coord)
        :g (.at img-data (+ coord 1))
        :b (.at img-data (+ coord 2))
        :a (.at img-data (+ coord 3))}))

   (defn white? [x y img-width img-data]
     (= {:r 255 :g 255 :b 255 :a 255} (get-rgba x y img-width img-data)))
   
   (defn scan-y [from-top? img-width img-height img-data]
     (loop [y (if from-top? 0 (dec img-height))
            colored-col nil]
       (if (and (not colored-col) (if from-top? (< y img-height) (< -1 y)))
         (recur
          (if from-top? (inc y) (dec y))
          (loop [x 0]
            (cond
              (not (white? x y img-width img-data)) y
              (< x (dec img-width)) (recur (inc x)))))
         colored-col)))

   (defn scan-x [from-left? img-width img-height img-data]
     (loop [x (if from-left? 0 (dec img-width))
            colored-row nil]
       (if (and (not colored-row) (if from-left? (< x img-width) (<= 0 x)))
         (recur
          (if from-left? (inc x) (dec x))
          (loop [y 0]
            (cond
              (not (white? x y img-width img-data)) x
              (< y (dec img-height)) (recur (inc y)))))
         colored-row)))
   
   (defn trim-image
     ([img] (trim-image img {}))
     ([img {:keys [padding] :or {padding 0}}]
      (let [canvas (js/document.createElement "canvas")
            ctx (.getContext canvas "2d")
            img-width (.-naturalWidth img)
            img-height (.-naturalHeight img)
            _ (.setAttribute canvas "width" img-width)
            _ (.setAttribute canvas "height" img-height)
            _ (.drawImage ctx img 0 0 img-width img-height)
            img-data (.-data (.getImageData ctx 0 0 img-width img-height))
            x1 (scan-x true img-width img-height img-data)
            y1 (scan-y true img-width img-height img-data)
            x2 (scan-x false img-width img-height img-data)
            y2 (scan-y false img-width img-height img-data)
            dx (inc (- x2 x1))
            dy (inc (- y2 y1))
            trimmed-data (.getImageData ctx x1 y1 dx dy)
            _ (.setAttribute canvas "width" (+ dx (* padding 2)))
            _ (.setAttribute canvas "height" (+ dy (* padding 2)))
            _ (.clearRect ctx 0 0 (+ dx padding) (+ dy padding))
            _ (set! (.-fillStyle ctx) "white")
            _ (.fillRect ctx 0 0 (.-width canvas) (.-height canvas))
            _ (.putImageData ctx trimmed-data padding padding)
            result-img (js/document.createElement "img")]
        (.setAttribute result-img "src" (.toDataURL canvas "image/png"))
        result-img)))

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
                                                     (.appendChild result (trim-image img))
                                                     (j/assoc! result-padding :innerHTML "")
                                                     (.appendChild result-padding (trim-image img {:padding 20}))))}]
         [:h4 "Trimmed"]
         [:div.flex
          [:div#result.border.border-red-500]]
         [:h4 "Trimmed with padding"]
         [:div.flex
          [:div#result-padding.border.border-red-500]]]])
     nil)))
