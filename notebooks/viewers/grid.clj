;; # 🔠 Grid Viewer
(ns ^:nextjournal.clerk/no-cache grid
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as v])
  (:import (java.net URL)
           (javax.imageio ImageIO)))

(def grid-viewer
  {:render-fn '(fn [items {:as opts :keys [num-cols]}]
                 (v/html (into [:div.md:grid.gap-6.mx-auto {:class (str "md:grid-cols-" num-cols)}]
                               (map (fn [item]
                                      [:div.flex.items-center.justify-center [v/inspect item]])
                                    items))))})

(clerk/with-viewer grid-viewer {::clerk/opts {:num-cols 2}}
  [(ImageIO/read (URL. "https://etc.usf.edu/clipart/36600/36667/thermos_36667_sm.gif"))
   (ImageIO/read (URL. "https://etc.usf.edu/clipart/186600/186669/186669-trees-in-the-winter_sm.gif"))
   (ImageIO/read (URL. "https://nextjournal.com/data/QmUyFWw9L8nZ6wvFTfJvtyqxtDyJiAr7EDZQxLVn64HASX?filename=Requiem-Ornaments-Byline.png&content-type=image/png"))])

^{::clerk/viewer grid-viewer ::clerk/opts {:num-cols 3} ::clerk/width :full}
[(ImageIO/read (URL. "https://etc.usf.edu/clipart/36600/36667/thermos_36667_sm.gif"))
 (ImageIO/read (URL. "https://etc.usf.edu/clipart/186600/186669/186669-trees-in-the-winter_sm.gif"))
 (ImageIO/read (URL. "https://nextjournal.com/data/QmUyFWw9L8nZ6wvFTfJvtyqxtDyJiAr7EDZQxLVn64HASX?filename=Requiem-Ornaments-Byline.png&content-type=image/png"))]
