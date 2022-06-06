;; # 🔠 Grid Viewer
(ns grid
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

#_(clerk/with-viewer grid-viewer
  [[(ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/allure-of-an-antique-empire_medium.jpg"))
    (ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/allegorical-symbol-for-the-emperos-victories-and-conquests_medium.jpg"))
    (ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/emperor-hadrian-getting-ready-for-a-day-of-hunting_medium.jpg"))]])

#_(clerk/with-viewer grid-viewer
  [[(ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/allure-of-an-antique-empire_medium.jpg"))
    (ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/allegorical-symbol-for-the-emperos-victories-and-conquests_medium.jpg"))]
   [(ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/emperor-hadrian-getting-ready-for-a-day-of-hunting_medium.jpg"))]])

#_(clerk/with-viewer grid-viewer
  [[(clerk/with-viewer grid-viewer
      [[(ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/allure-of-an-antique-empire_medium.jpg"))]
       [(ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/allegorical-symbol-for-the-emperos-victories-and-conquests_medium.jpg"))]])
    (clerk/with-viewer grid-viewer
      [[(ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/emperor-hadrian-getting-ready-for-a-day-of-hunting_medium.jpg"))
        (ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/fregio-ingressus_medium.jpg"))]
       [(ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/fregio-profectio_medium.jpg"))
        (ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/fourth-fregio-of-trajan_medium.jpg"))]])]])

#_(clerk/with-viewer grid-viewer
  [[(clerk/with-viewer grid-viewer
      [[(ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/allure-of-an-antique-empire_medium.jpg"))]
       [(ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/allegorical-symbol-for-the-emperos-victories-and-conquests_medium.jpg"))]])
    (clerk/with-viewer grid-viewer
      [[(ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/emperor-hadrian-getting-ready-for-a-day-of-hunting_medium.jpg"))]
       [(ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/fregio-ingressus_medium.jpg"))]
       [(ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/fregio-profectio_medium.jpg"))]
       [(ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/fourth-fregio-of-trajan_medium.jpg"))]])]])

(def grid-row
  {:name :grid/row
   :render-fn '(fn [items opts]
                 (let [item-count (count items)]
                   (v/html (into [:div {:class "md:flex md:flex-row md:gap-4 not-prose"}]
                             (map (fn [item]
                                    [:div.flex.items-center.justify-center
                                     {:class (str "md:w-[" (* 100 (float (/ 1 item-count))) "%]")}
                                     (v/inspect opts item)])) items))))})

(def grid-col
  {:name :grid/col
   :render-fn '(fn [items opts]
                 (v/html (into [:div {:class "md:flex md:flex-col md:gap-4 clerk-grid not-prose"}]
                           (map (fn [item]
                                  [:div.flex.items-center.justify-center
                                   (v/inspect opts item)])) items)))})

(clerk/with-viewer grid-row
  [(ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/allure-of-an-antique-empire_medium.jpg"))
   (ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/allegorical-symbol-for-the-emperos-victories-and-conquests_medium.jpg"))
   (ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/emperor-hadrian-getting-ready-for-a-day-of-hunting_medium.jpg"))])

(clerk/with-viewer grid-col
  [(ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/allure-of-an-antique-empire_medium.jpg"))
   (ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/allegorical-symbol-for-the-emperos-victories-and-conquests_medium.jpg"))
   (ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/emperor-hadrian-getting-ready-for-a-day-of-hunting_medium.jpg"))])

(clerk/with-viewer grid-col
  [(clerk/with-viewer grid-row [(ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/allure-of-an-antique-empire_medium.jpg"))
                                (ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/allegorical-symbol-for-the-emperos-victories-and-conquests_medium.jpg"))
                                (ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/emperor-hadrian-getting-ready-for-a-day-of-hunting_medium.jpg"))])
   (clerk/with-viewer grid-row [(ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/fregio-ingressus_medium.jpg"))])
   (clerk/with-viewer grid-row [(ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/fregio-profectio_medium.jpg"))
                                (ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/fourth-fregio-of-trajan_medium.jpg"))])])

(clerk/with-viewer grid-col
  [(clerk/with-viewer grid-row [(ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/allure-of-an-antique-empire_medium.jpg"))
                                (ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/allegorical-symbol-for-the-emperos-victories-and-conquests_medium.jpg"))
                                (ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/emperor-hadrian-getting-ready-for-a-day-of-hunting_medium.jpg"))
                                (ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/fregio-ingressus_medium.jpg"))])
   (clerk/with-viewer grid-row [(ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/fregio-profectio_medium.jpg"))
                                (ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/fourth-fregio-of-trajan_medium.jpg"))])])

(clerk/with-viewer grid-row
  [(clerk/with-viewer grid-col {:width "25%"} [(ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/allure-of-an-antique-empire_medium.jpg"))
                                               (ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/allegorical-symbol-for-the-emperos-victories-and-conquests_medium.jpg"))
                                               (ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/emperor-hadrian-getting-ready-for-a-day-of-hunting_medium.jpg"))
                                               (ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/fregio-ingressus_medium.jpg"))])
   (clerk/with-viewer grid-col [(ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/fregio-profectio_medium.jpg"))
                                (ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/fourth-fregio-of-trajan_medium.jpg"))])])