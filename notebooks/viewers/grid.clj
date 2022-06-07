;; # 🔠 Grid Layouts
(ns grid
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as v])
  (:import (java.net URL)
           (javax.imageio ImageIO)))

;; ## Layouts can be composed via `row`s and `col`s
;;
;; Passing `:width`, `:height` or any other style attributes to `::clerk/opts`
;; will assign them on the row or col that contains your items. You can use
;; this to size your containers accordingly.

#(v/row
   (ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/allure-of-an-antique-empire_medium.jpg"))
   (ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/allegorical-symbol-for-the-emperos-victories-and-conquests_medium.jpg"))
   (ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/emperor-hadrian-getting-ready-for-a-day-of-hunting_medium.jpg")))

(v/col
  {::clerk/opts {:width 150}}
  (ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/allure-of-an-antique-empire_medium.jpg"))
  (ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/allegorical-symbol-for-the-emperos-victories-and-conquests_medium.jpg"))
  (ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/emperor-hadrian-getting-ready-for-a-day-of-hunting_medium.jpg")))

(v/col
  (v/row
    (ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/allure-of-an-antique-empire_medium.jpg"))
    (ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/allegorical-symbol-for-the-emperos-victories-and-conquests_medium.jpg"))
    (ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/emperor-hadrian-getting-ready-for-a-day-of-hunting_medium.jpg")))
  (v/row
    (ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/fregio-ingressus_medium.jpg")))
  (v/row
    (ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/fregio-profectio_medium.jpg"))
    (ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/fourth-fregio-of-trajan_medium.jpg"))))

(v/col
  (v/row
    (ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/allure-of-an-antique-empire_medium.jpg"))
    (ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/allegorical-symbol-for-the-emperos-victories-and-conquests_medium.jpg"))
    (ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/emperor-hadrian-getting-ready-for-a-day-of-hunting_medium.jpg"))
    (ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/fregio-ingressus_medium.jpg")))
  (v/row
    (ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/fregio-profectio_medium.jpg"))
    (ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/fourth-fregio-of-trajan_medium.jpg")))
  (v/row
    (ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/fregio-profectio_medium.jpg"))
    (ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/fourth-fregio-of-trajan_medium.jpg"))))

(v/row
  (v/col
    {::clerk/opts {:width 100}}
    (ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/allure-of-an-antique-empire_medium.jpg"))
    (ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/allegorical-symbol-for-the-emperos-victories-and-conquests_medium.jpg"))
    (ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/emperor-hadrian-getting-ready-for-a-day-of-hunting_medium.jpg"))
    (ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/fregio-ingressus_medium.jpg")))
  (v/col
    (ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/fregio-profectio_medium.jpg"))
    (ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/fourth-fregio-of-trajan_medium.jpg"))))

;; ## Alternative notations
;;
;; By default, `row` and `col` operate on `& rest` so you can pass any number of items to the functions.
;; But the viewers are smart enough to accept any sequential list of items so you can
;; assign the viewers via metadata on your data structures too.

^{::clerk/viewer :row}
[(ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/allure-of-an-antique-empire_medium.jpg"))
 (ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/allegorical-symbol-for-the-emperos-victories-and-conquests_medium.jpg"))
 (ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/emperor-hadrian-getting-ready-for-a-day-of-hunting_medium.jpg"))
 (ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/fregio-ingressus_medium.jpg"))]

^{::clerk/viewer v/row}
[(ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/allure-of-an-antique-empire_medium.jpg"))
 (ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/allegorical-symbol-for-the-emperos-victories-and-conquests_medium.jpg"))
 (ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/emperor-hadrian-getting-ready-for-a-day-of-hunting_medium.jpg"))
 (ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/fregio-ingressus_medium.jpg"))]

(v/row
  [(ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/allure-of-an-antique-empire_medium.jpg"))
   (ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/allegorical-symbol-for-the-emperos-victories-and-conquests_medium.jpg"))
   (ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/emperor-hadrian-getting-ready-for-a-day-of-hunting_medium.jpg"))
   (ImageIO/read (URL. "https://etc.usf.edu/clippix/pix/fregio-ingressus_medium.jpg"))])