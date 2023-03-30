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

;; ## Code Listings

;; ```
;; {:name :code,
;;  :render-fn 'nextjournal.clerk.render/render-code,
;;  :transform-fn
;;  (comp
;;   mark-presented
;;   (update-val
;;    (fn
;;      [v]
;;      (if (string? v) v (str/trim (with-out-str (pprint/pprint v)))))))}
;; ```

;; ## Soft vs. Hard Line Breaks
;; This one ⇥
;; ⇤ is a [soft break](https://spec.commonmark.org/0.30/#soft-line-breaks) and is rendered as a space.
;;
;; This one instead ⇥\
;; ⇤ is a [hard break](https://spec.commonmark.org/0.30/#hard-line-breaks) and is rendered as a newline.

;; ## Sidenotes
;;
;; One of the most distinctive features of Tufte’s style is his _extensive use
;; of sidenotes_[^sidenote]. Sidenotes are like footnotes,
;; except they don’t force the reader to jump their eye to the bottom of the
;; page, but instead display off to the side in the margin. Perhaps you have
;; noticed their use in this document already^[If you are _astute_ enough!]. You are very astute.
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
;;
;; We also have to consider however that sidenotes can be part of a blockquote,
;; like so:
;; > “The purpose of computation is insight, not numbers.” [^hamming]
;; >
;; > ― Richard Hamming
;;
;; [^hamming]: From _"The Art of Doing Science and Engineering: Learning to Learn"_
;; by Richard Hamming
;;
;; And it can be followed by lists so the list layout also has to adapt to the
;; new content width once a sidenote is present in the document:
;;
;; Things to do:

;; * Hire two private investigators. Get them to follow each other.
;; * Wear t-shirt that says "Life". Hand out lemons^[not oranges] on street corner.
;;   * Wear t-shirt that says "Life". Hand out lemons^[not oranges] on street corner.
;; * Change name to Simon. Speak in thirs person.
;; * Major in philosophy. Ask people WHY they would like fries with that.

;; ## Code Listings As Markdown Result

^{::clerk/visibility {:code :hide}}
(clerk/md "```sh
clj -M:nextjournal/clerk nextjournal.clerk/serve! --watch-paths notebooks --browse
```")

(clerk/md "---")

;; ### Conclusion^[what usually average folks actually read.]
;; Sidenote references should not be resetted[^crossnote] across code blocks.
;;
;; | Tables   |     Are  |  Cool |
;; |----------|:---------|------:|
;; | col 2 is | left[^*] |  1600 |
;; | col 3 is |  right   |    12 |
;;
;; [^crossnote]: as in: _1-based_ again.
;; [^*]: as in _not right_.
;;
;; ## Internal Links
;; Clerk extends markdown parsing with a wikipedia-style `[[internal-link]]`. The text between the double brackets can be
;; * a path to a notebook on the classpath ([[notebooks/markdown.md]])
;; * a namespace on the classpath ([[viewers.html]])
;; * a fully qualified symbol resolving to a var ([[how-clerk-works/hashes]])
;; in all cases the rendered link points to the associated notebook. In the third case an hash fragment is appended pointing to the block which defines the var in question.
