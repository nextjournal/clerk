;; # Image Layouts 🎑 🌁 🏞
;;
;; A list of image samples in different layouts that best suit the images
;; aspect ratio.
(ns image-layouts
  {:nextjournal.clerk/visibility {:code :fold}}
  (:require [nextjournal.clerk :as clerk]))

;; ## Viewport width, height based on aspect ratio (or fixed)
;; * Examples: `3:1`, `4:1`
;; * Default for `2 < ratio` and `content width < intrinsic width`

(merge
  {:nextjournal/width :full}
  (clerk/html
    [:figure
     [:img
      {:class "w-full"
       :src "https://images.unsplash.com/photo-1532879311112-62b7188d28ce?ixlib=rb-1.2.1&ixid=MnwxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8"}]
     [:figcaption.max-w-prose.mx-auto.px-8.text-sm.text-slate-500.sans-serif.mt-2
      "Viewport width, height based on aspect ratio"]]))

(merge
  {:nextjournal/width :full}
  (clerk/html
    [:figure
     [:img
      {:class "w-full object-cover h-[300px]"
       :src "https://images.unsplash.com/photo-1532879311112-62b7188d28ce?ixlib=rb-1.2.1&ixid=MnwxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8"}]
     [:figcaption.max-w-prose.mx-auto.px-8.text-sm.text-slate-500.sans-serif.mt-2
      "Viewport width, fixed height"]]))

;; ## Content width, height based on aspect ratio (or fixed)
;; * Examples: `1.91:1`, `2:1`, `3:2`, `4:3`, `16:9`
;; * Default for `1 < ratio < 2` and `content width <= intrinsic width`

(clerk/html
  [:figure
   [:img
    {:class "w-full"
     :src "https://images.freeimages.com/images/large-previews/773/koldalen-4-1384902.jpg"}]
   [:figcaption.max-w-prose.text-sm.text-slate-500.sans-serif.mt-2
    "Content width, height based on aspect ratio"]])

(clerk/html
  [:figure
   [:img
    {:class "w-full object-cover h-[300px]"
     :src "https://images.freeimages.com/images/large-previews/773/koldalen-4-1384902.jpg"}]
   [:figcaption.text-sm.text-slate-500.sans-serif.mt-2
    "Content width, fixed height"]])

;; ## Intrinsic dimensions (or fixed dimensions)
;; * Examples: `1:1`, `2:3`, `9:16`
;; * Default for `ratio <= 1` and `intrinsic width <= content width`

(clerk/html
  [:figure.flex.flex-col.items-center
   [:img
    {:src "https://etc.usf.edu/clipart/36600/36667/thermos_36667_sm.gif"}]
   [:figcaption.text-sm.text-slate-500.sans-serif.mt-2.text-center
    "Intrinsic width, height based on aspect ratio"]])

(clerk/html
  [:figure.flex.flex-col.items-center
   [:img.object-contain
    {:class "object-contain w-[300px] h-[300px]"
     :src "https://etc.usf.edu/clipart/186600/186669/186669-trees-in-the-winter_sm.gif"}]
   [:figcaption.text-sm.text-slate-500.sans-serif.mt-2.text-center
    "Fixed width and height"]])

;; ## Bonus Mission: Grids!
;; * auto-sizes based on content width
;; * or uses fixed cell dimensions
;; * Default layouts: single row for `image count < 4`, then adds rows based on count

;; ### Single row, auto-sizing cells

(clerk/html
  [:div.grid.grid-cols-3.gap-6
   [:figure.flex.flex-col.items-center.justify-end
    [:img
     {:class "object-contain"
      :src "https://etc.usf.edu/clipart/16200/16224/snowflake3_16224_sm.gif"}]
    [:figcaption.text-sm.text-slate-500.sans-serif.mt-2.text-center
     "1-1"]]
   [:figure.flex.flex-col.items-center.justify-end
    [:img
     {:class "object-contain"
      :src "https://etc.usf.edu/clipart/16200/16226/snowflake5_16226_sm.gif"}]
    [:figcaption.text-sm.text-slate-500.sans-serif.mt-2.text-center
     "1-2"]]
   [:figure.flex.flex-col.items-center.justify-end
    [:img
     {:class "object-contain"
      :src "https://etc.usf.edu/clipart/16200/16228/snowflake6_16228_sm.gif"}]
    [:figcaption.text-sm.text-slate-500.sans-serif.mt-2.text-center
     "1-3"]]])

;; ### Multiple rows, fixed layout (4 columns), fixed cell size

(clerk/html
  [:div.grid.grid-cols-4.gap-6
   [:figure.flex.flex-col.items-center.justify-end
    [:img
     {:class "object-contain w-[100px] h-[100px]"
      :src "https://etc.usf.edu/clipart/16200/16224/snowflake3_16224_sm.gif"}]
    [:figcaption.text-sm.text-slate-500.sans-serif.mt-2.text-center
     "1-1"]]
   [:figure.flex.flex-col.items-center.justify-end
    [:img
     {:class "object-contain w-[100px] h-[100px]"
      :src "https://etc.usf.edu/clipart/16200/16226/snowflake5_16226_sm.gif"}]
    [:figcaption.text-sm.text-slate-500.sans-serif.mt-2.text-center
     "1-2"]]
   [:figure.flex.flex-col.items-center.justify-end
    [:img
     {:class "object-contain w-[100px] h-[100px]"
      :src "https://etc.usf.edu/clipart/16200/16228/snowflake6_16228_sm.gif"}]
    [:figcaption.text-sm.text-slate-500.sans-serif.mt-2.text-center
     "1-3"]]
   [:figure.flex.flex-col.items-center.justify-end
    [:img
     {:class "object-contain w-[100px] h-[100px]"
      :src "https://etc.usf.edu/clipart/16200/16215/snowflake1_16215_sm.gif"}]
    [:figcaption.text-sm.text-slate-500.sans-serif.mt-2.text-center
     "1-4"]]
   [:figure.flex.flex-col.items-center.justify-end
    [:img
     {:class "object-contain w-[100px] h-[100px]"
      :src "https://etc.usf.edu/clipart/16200/16223/snowflake2_16223_sm.gif"}]
    [:figcaption.text-sm.text-slate-500.sans-serif.mt-2.text-center
     "2-1"]]
   [:figure.flex.flex-col.items-center.justify-end
    [:img
     {:class "object-contain w-[100px] h-[100px]"
      :src "https://etc.usf.edu/clipart/16200/16229/snowflake7_16229_sm.gif"}]
    [:figcaption.text-sm.text-slate-500.sans-serif.mt-2.text-center
     "2-2"]]
   [:figure.flex.flex-col.items-center.justify-end
    [:img
     {:class "object-contain w-[100px] h-[100px]"
      :src "https://etc.usf.edu/clipart/16200/16236/snowflake13_16236_sm.gif"}]
    [:figcaption.text-sm.text-slate-500.sans-serif.mt-2.text-center
     "2-3"]]
   [:figure.flex.flex-col.items-center.justify-end
    [:img
     {:class "object-contain w-[100px] h-[100px]"
      :src "https://etc.usf.edu/clipart/16200/16237/snowflake14_16237_sm.gif"}]
    [:figcaption.text-sm.text-slate-500.sans-serif.mt-2.text-center
     "2-4"]]])

#_(nextjournal.clerk/show! "notebooks/viewers/image_layouts.clj")
