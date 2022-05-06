;; # Markdown ✍️
(ns markdown (:require [nextjournal.clerk :as clerk]))

(clerk/md "### Text can be\n * **bold**\n * *italic*\n * ~~Strikethrough~~\n
It's [Markdown](https://daringfireball.net/projects/markdown/), like you know it.")

;; ## Testing Markdown rendering for different output methods

;; ### A heading and a paragraph in one comment block
;; A heading and a paragraph in one comment block

;; ### A heading and a paragraph separated by a newline
;; A heading and a paragraph separated by a newline

(clerk/md
  "### A heading and a paragraph as result
   A heading and a paragraph as result")

(clerk/md
  "### A heading and a paragraph as result, separated by a newline

   A heading and a paragraph as result, separated by a newline")

;; ## Heading Sizes
;; ### Heading 3
;; #### Heading 4
;; ##### Heading 5

;; ## Blockquotes

;; > If the recursion point was a `fn` method, then it rebinds the params.
;; >
;; > — Special Forms