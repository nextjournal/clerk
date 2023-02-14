;; # 🏞 Image Viewer
(ns image
  (:require [babashka.fs :as fs]
            [nextjournal.clerk :as clerk])
  (:import (javax.imageio ImageIO)
           (java.awt.image BufferedImage)
           (java.net URL)))

;; Clerk now comes with a default viewer for `java.awt.image.BufferedImage`. It looks at the dimensions of the image, and tries to do the right thing. For an image larger than 900px wide with an aspect ratio larger 2, it uses full width.
(ImageIO/read (URL. "https://images.unsplash.com/photo-1532879311112-62b7188d28ce?ixlib=rb-1.2.1&ixid=MnwxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8"))

;; For convenience, you can use the `clerk/image` function to create a buffered image from a string or anything `java.awt.image.BufferedImage/read` takes. You can also use `clerk/figure` to show the image with a descriptive caption below it.

;; A large image with an aspect ratio smaller 2 is shown wider.
(clerk/image "https://images.freeimages.com/images/large-previews/773/koldalen-4-1384902.jpg")

;; Smaller images are centered and shown using their intrinsic dimensions. Here, we're using `clerk/figure`:

(clerk/image "https://nextjournal.com/data/QmeyvaR3Q5XSwe14ZS6D5WBQGg1zaBaeG3SeyyuUURE2pq?filename=thermos.gif&content-type=image/gif")

;; Layout options are also available. For example, `{::clerk/width :full}` renders the image in full width.

(clerk/image {::clerk/width :full} "https://images.freeimages.com/images/large-previews/773/koldalen-4-1384902.jpg")

;; ## Markdown Images with local Files
;; Mardown images can refer to local files in their source. Therefore, provided we have

(ImageIO/write (BufferedImage. 20 20 BufferedImage/TYPE_BYTE_GRAY) "jpg"
               (fs/file "images/random.jpg"))
;; this ![alt](images/random.jpg) should to the job.
