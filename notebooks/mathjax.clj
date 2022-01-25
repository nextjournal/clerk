;; # MathJax
;; How to set default latex renderer to MathJax
(ns ^:nextjournal.clerk/no-cache mathjax
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as clerk-viewer]))

(clerk/set-viewers! [{:name :latex
                      :render-fn (quote v/mathjax-viewer)
                      :fetch-fn (fn [_ x] x)}])

;; this is **strong** _cursive_ text

;; - this is an inline formula $\phi$
;; - in a list

(clerk/tex "\\begin{equation}
 \\cos \\theta_1 = \\cos \\theta_2 \\implies \\theta_1 = \\theta_2
 \\label{eq:cosinjective}
 \\tag{COS-INJ}
 \\end{equation}")

;; As explained in $\eqref{eq:cosinjective}$.
