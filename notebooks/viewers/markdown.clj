;; # Markdown ✍️
^{:nextjournal.clerk/toc true}
(ns markdown (:require [nextjournal.clerk :as clerk]))

;; ## Using line comments

;; In Clerk, by default, anything that is inside a `;;` line comment is interpreted as Markdown.
;; Styles are automatically applied using Tailwind’s typography plugin.
;; This means using a `## h4 heading` will look like an `h4` heading, etc:

;; ## A h2 heading

;; ## Explicit viewer output

;; Instead of using line comments, you can also use Clerk’s `clerk/md` viewer, like so:

(clerk/md "### Text can be\n * **bold**\n * *italic*\n * ~~Strikethrough~~\n
It's [Markdown](https://daringfireball.net/projects/markdown/), like you know it.")

;; No matter if you use line comments or `clerk/md`, the output should always look the same.

;; ## Disabled default styles for specific cases
;;
;; If you want to opt-out of the default styles (let’s say you’re using a `html` viewer that
;; shouldn’t use the default styling) you can do that by applying the `not-prose` class to
;; the HTML or hiccup output’s parent element.

;; Here’a an example:
;; By default, the `table` will have tailwind’s default table styles (in this case a default width) applied

(clerk/html [:table
             [:tr [:td "◤"] [:td "◥"]]
             [:tr [:td "◉"] [:td "◉"]]
             [:tr [:td "◣"] [:td "◢"]]])

;; but providing a parent element that has the `not-prose` class set will remove all default styling:

(clerk/html [:div.not-prose
             [:table
              [:tr [:td "◤"] [:td "◥"]]
              [:tr [:td "◉"] [:td "◉"]]
              [:tr [:td "◣"] [:td "◢"]]]])

;; ## Comprehensive Markdown Demo

;; Paragraphs are separated by a blank line.
;;
;; 2nd paragraph. *Italic*, **bold**, and `monospace`. Itemized lists
;; look like:
;;
;;   * this one
;;   * that one
;;   * the other one
;;
;; Note that — not considering the asterisk — the actual text
;; content starts at 4-columns in.
;;
;;   > Block quotes are
;;   > written like so.
;;   >
;;   > They can span multiple paragraphs,
;;   > if you like.
;;
;; ## An h2 header
;;
;; Here's a numbered list:
;;
;;   1. first item
;;   2. second item
;;   3. third item
;;
;; ### An h3 header
;;
;; Now a nested list:
;;
;; 1. First, get these ingredients:
;;
;;     * carrots
;;     * celery
;;     * lentils
;;
;; 2. Boil some water.
;;
;; 3. Dump everything in the pot and follow this algorithm:
;;
;;     * find wooden spoon
;;     * uncover pot
;;     * stir
;;     * cover pot
;;     * balance wooden spoon precariously on pot handle
;;     * wait 10 minutes
;;     * goto first step (or shut off burner when done)
;;
;; 4. Do not bump wooden spoon or it will fall.
;;
;; Here's a link to [a website](http://foo.bar), to a [local doc](local-doc.html),
;; and to a [section heading in the current doc](#an-h2-header).
;;
;; ---
;;
;; ## A heading following a ruler
;;
;; Inline math equation: $\omega = d\phi / dt$. Display
;; math should get its own line like so:
;;
;; $$I = \int \rho R^{2} dV$$
;;
;; And note that you can backslash-escape any punctuation characters
;; which you wish to be displayed literally, ex.: \`foo\`, \*bar\*, etc.

;; # Test: Different Output Methods

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

;; ## Heading 1
;; Some text following the heading
;; ### Heading 3
;; Some text following the heading
;; #### Heading 4
;; Some text following the heading
;; ##### Heading 5
;; Some text following the heading