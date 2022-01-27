;; # Markdown Viewer ✍️

^{:nextjournal.clerk/visibility #{:hide-ns}}
(ns ^:nextjournal.clerk/no-cache viewers.markdown
  (:require [nextjournal.clerk :as clerk]))

;; ## Markdown Comments
;; Clerk parses all _consecutive_ `;;`-led _clojure comment lines_ as [Markdown](https://daringfireball.net/projects/markdown). All of markdown syntax should be supported:
;; ### Lists
;; - one **strong**
;; - two ~~strikethrough~~
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

(clerk/md "#### Text can be\n * **bold**\n * *italic*\n * ~~Strikethrough~~\n
It's [Markdown](https://daringfireball.net/projects/markdown/), like you know it.")

;; ---
;; ## Overriding Markdown Viewers
;; What's wrong with paragraph colors in this notebook? It's possible to override each markdown node viewer via `set-viewers!`, and this will instantly have effect on the whole document.
(clerk/set-viewers! [{:name :nextjournal.markdown/paragraph
                      :render-fn '(fn [{:keys [content]} opts]
                                    (v/html
                                     (into [:p {:style {:color "#0c4a6e"
                                                        :font-size "110%"}}]
                                           (v/inspect-children opts)
                                           content)))}

                     {:name :nextjournal.markdown/ruler
                      :render-fn '(constantly
                                   (v/html
                                    [:hr {:style {:border "3px solid #f472b6"}}]))}
                     {:name :latex
                      :render-fn 'v/mathjax-viewer}])
;; ### Current State
;; As an excuse to test _tables_ in markdown:
;;
;; | feature                                                                                                            |    |
;; |:-------------------------------------------------------------------------------------------------------------------|:---|
;; | Reactively update document when markdown viewers change                                                            |✅  |
;; | Chaning viewers markdown depends on reactively update the document (see `:latex` viewer for $\phi$-or-$\mu$-ulas)  |✅  |
;; | Expose convenient helpers to compose markup                                                                        |❌  |
;;
;; ---
