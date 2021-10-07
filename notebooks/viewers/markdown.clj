;; # Markdown ✍️
(ns markdown (:require [nextjournal.clerk :as clerk]))

(clerk/md "### Text can be\n * **bold**\n * *italic\n * ~~Strikethrough~~\n
It's [Markdown](https://daringfireball.net/projects/markdown/), like you know it.")
