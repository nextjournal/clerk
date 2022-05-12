;; # Customizable Markdown
;;
;; Playground for overriding markdown nodes

^{:nextjournal.clerk/visibility #{:hide-ns}}
(ns ^:nextjournal.clerk/no-cache viewers.custom-markdown
  (:require [nextjournal.clerk.viewer :as v]))

#_(defn update-markdown-viewer [update-fn]
    {(comp #{:markdown} :name) (fn [v] (update v :update-viewers-fn
                                               (fn [old-fn]
                                                 (fn [viewers] (update-fn (old-fn viewers))))))})

(defn update-child-viewers [update-fn]
  (fn [v] (update v :update-viewers-fn (fn [old-fn]
                                         (fn [viewers] (update-fn (old-fn viewers)))))))

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

(def viewers-with-pretty-markdown
  (v/update-viewers v/default-viewers {(comp #{:markdown} :name)
                                       (update-child-viewers #(v/add-viewers % md-viewers))}))


(= v/default-viewers
   (-> (v/with-viewers v/default-viewers
         [1 2 3])
       v/->viewers)
   #_
   (v/with-viewer {:update-viewers-fn (constantly v/default-viewers)}
     [1 2 3])
   #_
   (v/->viewers (v/apply-viewers (v/with-viewer {:update-viewers-fn (constantly v/default-viewers)}
                                   [1 2 3]))))

#_(v/reset-viewers! viewers-with-pretty-markdown)

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
