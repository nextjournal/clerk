;; # Markdown 🗺
(require '[nextjournal.viewer :as v])

(v/view-as :markdown
           "### Text can be\n * **bold**\n * *italic\n * ~~Strikethrough~~\n
It's [Markdown](https://daringfireball.net/projects/markdown/), like you know it.")
