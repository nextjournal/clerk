;; # Customizable Markdown
;;
;; Playground for overriding markdown nodes

^{:nextjournal.clerk/visibility #{:hide-ns}}
(ns ^:nextjournal.clerk/no-cache viewers.custom-markdown
  (:require [nextjournal.clerk.viewer :as v]))

(defn update-child-viewers [f]
  (fn [viewer]
    (update viewer :transform-fn (fn [transform-fn]
                                   (fn [wrapped-value]
                                     (-> wrapped-value
                                         transform-fn
                                         (update :nextjournal/viewers f)))))))

(def md-viewers
  [{:name :nextjournal.markdown/text
    :transform-fn (v/into-markup [:span {:style {:color "#64748b"}}])}
   {:name :nextjournal.markdown/ruler
    :transform-fn (constantly
                   (v/html [:div {:style {:width "100%" :height "80px" :background-position "center" :background-size "cover"
                                          :background-image "url(https://www.maxpixel.net/static/photo/1x/Ornamental-Separator-Decorative-Line-Art-Divider-4715969.png)"}}]))}])

(def viewers-with-pretty-markdown
  (v/update-viewers (v/get-default-viewers) {(comp #{:markdown} :name)
                                             (update-child-viewers #(v/add-viewers % md-viewers))}))

(v/reset-viewers! viewers-with-pretty-markdown)

;; ## Sections
;;
;; with some _more_ text and a ruler.
;;
;; ---
;;
;; Clerk parses all _consecutive_ `;;`-led _clojure comment lines_ as [Markdown](https://daringfireball.net/projects/markdown). All of markdown syntax should be supported:
;;
;; ### Lists
;;
;; - one **strong** hashtag like #nomarkdownforoldcountry
;; - two ~~strikethrough~~
;;
;; and bullet lists
;;
;; - [x] what
;; - [ ] the
;;
;; ### Code
;;
;;     (assoc {:this "should get"} :clojure "syntax highlighting")
;;
;; ---
;;
;; ### Tables
;;
;; Tables with specific alignment.
;;
;; | feature |    |
;; |:-------:|:---|
;; |   One   |✅  |
;; |   Two   |✅  |
;; |  Three  |❌  |
;;
;; ---
