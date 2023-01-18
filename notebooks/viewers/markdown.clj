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

;; ## Sidenotes

;; One of the most distinctive features of Tufte’s style is his extensive use
;; of sidenotes [^sidenote]. This is a sidenote. Sidenotes are like footnotes,
;; except they don’t force the reader to jump their eye to the bottom of the
;; page, but instead display off to the side in the margin. Perhaps you have
;; noticed their use in this document already. You are very astute.
;; 
;; [^sidenote]: This is a sidenote. The purpose of this text is to
;; merely demonstrate the use of sidenotes. All text was originally published
;; on the [Tufte CSS website](https://edwardtufte.github.io/tufte-css/).

;;
;; Sidenotes are a great example of the web not being like print. On sufficiently
;; large viewports, Tufte CSS uses the margin for sidenotes, margin notes, and
;; small figures. On smaller viewports, elements that would go in the margin are
;; hidden until the user toggles them into view. The goal is to present related
;; but not necessary information such as asides or citations as close as possible
;; to the text that references them. At the same time, this secondary information
;; should stay out of the way of the eye, not interfering with the progression of
;; ideas in the main text.









