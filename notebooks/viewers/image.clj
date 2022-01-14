;; # 🏞 Image Viewer
(ns ^:nextjournal.clerk/no-cache image
  (:import (java.net URL)
           (javax.imageio ImageIO)))

;; Clerk now comes with a default viewer for `java.awt.image.BufferedImage`.
(ImageIO/read (URL. "https://images.unsplash.com/photo-1532879311112-62b7188d28ce?ixlib=rb-1.2.1&ixid=MnwxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8"))

#_(nextjournal.clerk/show! "notebooks/viewers/image.clj")
