;; # 🔠 Grid Viewer
(ns ^:nextjournal.clerk/no-cache grid
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as v])
  (:import (java.net URL)
           (javax.imageio ImageIO)))

(defn update-grid-viewers [viewers]
  (v/add-viewers viewers [{:name :grid/markup
                           :render-fn '(fn [rows opts]
                                         (v/html (into [:div]
                                                   (v/inspect-children opts) rows)))}
                          {:name :grid/row
                           :render-fn '(fn [row opts]
                                         (let [cols (count row)]
                                           (v/html (into [:div {:class "md:flex md:gap-4"}]
                                                     (map (fn [item] [:div
                                                                      {:class (str "md:w-[" (* 100 (float (/ 1 cols))) "%]")}
                                                                      (v/inspect opts item)])) row))))}]))

(def grid-viewer
  {:name :grid
   :transform-fn (fn [wrapped-value]
                   (let [rows (v/->value wrapped-value)]
                     (-> wrapped-value
                       (assoc :nextjournal/viewer :grid/markup)
                       (update :nextjournal/width #(or % :wide))
                       (update :nextjournal/viewers update-grid-viewers)
                       (assoc :nextjournal/value (map (partial v/with-viewer :grid/row) rows)))))})

(clerk/with-viewer grid-viewer
  [[(ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/allure-of-an-antique-empire_medium.jpg"))
    (ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/allegorical-symbol-for-the-emperos-victories-and-conquests_medium.jpg"))
    (ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/emperor-hadrian-getting-ready-for-a-day-of-hunting_medium.jpg"))]])

(clerk/with-viewer grid-viewer
  [[(ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/allure-of-an-antique-empire_medium.jpg"))
    (ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/allegorical-symbol-for-the-emperos-victories-and-conquests_medium.jpg"))]
   [(ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/emperor-hadrian-getting-ready-for-a-day-of-hunting_medium.jpg"))]])

(clerk/with-viewer grid-viewer
  [[(clerk/with-viewer grid-viewer
      [[(ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/allure-of-an-antique-empire_medium.jpg"))]
       [(ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/allegorical-symbol-for-the-emperos-victories-and-conquests_medium.jpg"))]])
    (clerk/with-viewer grid-viewer
      [[(ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/emperor-hadrian-getting-ready-for-a-day-of-hunting_medium.jpg"))
        (ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/fregio-ingressus_medium.jpg"))]
       [(ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/fregio-profectio_medium.jpg"))
        (ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/fourth-fregio-of-trajan_medium.jpg"))]])]])

(clerk/with-viewer grid-viewer
  [[(clerk/with-viewer grid-viewer
      [[(ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/allure-of-an-antique-empire_medium.jpg"))]
       [(ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/allegorical-symbol-for-the-emperos-victories-and-conquests_medium.jpg"))]])
    (clerk/with-viewer grid-viewer
      [[(ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/emperor-hadrian-getting-ready-for-a-day-of-hunting_medium.jpg"))]
       [(ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/fregio-ingressus_medium.jpg"))]
       [(ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/fregio-profectio_medium.jpg"))]
       [(ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/fourth-fregio-of-trajan_medium.jpg"))]])]])