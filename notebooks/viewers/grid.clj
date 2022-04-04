(ns ^:nextjournal.clerk/no-cache grid
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as v]
            [backtick :as backtick])
  (:import (java.net URL)
           (javax.imageio ImageIO)))

(defn grid-viewer
  ([] (grid-viewer {:cols 2}))
  ([{:keys [cols]}]
   {:render-fn (backtick/template
                 (fn [items opts]
                   (v/html
                     (into [:div.md:grid.gap-6.mx-auto
                            {:class ~(str "md:grid-cols-" cols)}]
                           (map (fn [item]
                                  [:div.flex.items-center.justify-center
                                   [v/inspect item]])
                                items)))))}))

^{::clerk/viewer (grid-viewer {:cols 3})}
[(ImageIO/read (URL. "https://etc.usf.edu/clipart/36600/36667/thermos_36667_sm.gif"))
 (ImageIO/read (URL. "https://etc.usf.edu/clipart/186600/186669/186669-trees-in-the-winter_sm.gif"))
 (ImageIO/read (URL. "https://nextjournal.com/data/QmUyFWw9L8nZ6wvFTfJvtyqxtDyJiAr7EDZQxLVn64HASX?filename=Requiem-Ornaments-Byline.png&content-type=image/png"))]

