;; # Markdown ✍️

^{:nextjournal.clerk/visibility #{:hide-ns}}
(ns ^:nextjournal.clerk/no-cache viewers.markdown
  (:require [nextjournal.clerk :as clerk]))

;; ## Markdown Comments
;; Clerk parses all _consecutive_ `;;`-led _clojure comment lines_ as [Markdown](https://daringfireball.net/projects/markdown). All of markdown syntax should be supported:
;; ### Lists
;; - one **strong**
;; - two ~~strikethrough~~
;; ### Todos
;; - [ ] one
;; - [x] checked two
;; ### Code
;; ```css
;; .viewer-markdown > :where(:last-child):not(:where([class~="not-prose"] *)) {
;;   margin-bottom: 0;
;; }
;; ```
;; ### Images
;; ![JCM](https://nextjournal.com/data/QmUyFWw9L8nZ6wvFTfJvtyqxtDyJiAr7EDZQxLVn64HASX?filename=Requiem-Ornaments-Byline.png&content-type=image/png)
;;
;; ---
;; ## Markdown Results
;; Clerk can in addition produce markdown result blocks

(clerk/md "#### Text can be\n * **bold**\n * *italic\n * ~~Strikethrough~~\n
It's [Markdown](https://daringfireball.net/projects/markdown/), like you know it.")

;; ---
;; ## Overriding Markdown Viewers
(clerk/set-viewers! [{:name :nextjournal.markdown/ruler
                      :render-fn '(constantly (v/html [:hr {:style {:border "3px solid #f472b6"}}]))}])
;; ---
