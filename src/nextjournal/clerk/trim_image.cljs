(ns nextjournal.clerk.trim-image
  "Utility functions to trim open graph preview images.")

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

(defn ^:export trim-image
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

(defn ^:export append-trimmed-image [base64 id]
  (let [img (js/document.createElement "img")]
    (.addEventListener img "load" (fn [event]
                                    (let [trimmed-img (trim-image (.-target event) {:padding 20})]
                                      (.setAttribute trimmed-img "id" id)
                                      (.. js/document -body (appendChild trimmed-img)))))
    (.setAttribute img "src" base64)))
