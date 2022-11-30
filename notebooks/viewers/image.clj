;; # 🏞 Image Viewer
(ns image
  (:import (java.net URL)
           (javax.imageio ImageIO)))

;; Clerk now comes with a default viewer for `java.awt.image.BufferedImage`. It looks at the dimensions of the image, and tries to do the right thing. For an image larger than 900px wide with an aspect ratio larger 2, it uses full width.
(ImageIO/read (URL. "https://images.unsplash.com/photo-1532879311112-62b7188d28ce?ixlib=rb-1.2.1&ixid=MnwxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8"))

;; A large image with an aspect ratio smaller 2 is shown wider.
(ImageIO/read (URL. "https://images.freeimages.com/images/large-previews/773/koldalen-4-1384902.jpg"))

;; Smaller images are centered and shown using thier intrinsic dimensions.
(ImageIO/read (URL. "https://nextjournal.com/data/QmeyvaR3Q5XSwe14ZS6D5WBQGg1zaBaeG3SeyyuUURE2pq?filename=thermos.gif&content-type=image/gif"))

(ImageIO/read (URL. "https://nextjournal.com/data/QmS7YdXsuN8Db5frzdny9XxUsDmTV7rq5fUG9XMoePWY5y?filename=trees.png&content-type=image/png"))

(ImageIO/read (URL. "https://nextjournal.com/data/QmUyFWw9L8nZ6wvFTfJvtyqxtDyJiAr7EDZQxLVn64HASX?filename=Requiem-Ornaments-Byline.png&content-type=image/png"))
