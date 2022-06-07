;; # 🔠 Grid Viewer
(ns ^:nextjournal.clerk/no-cache grid
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as v])
  (:import (java.net URL)
           (javax.imageio ImageIO)))

(v/row
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