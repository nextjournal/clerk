;; # ${\LaTeX}$ with MathJax
;; Thisi is how to set Clerk's latex rendering to use [MathJax](https://mathjax.org). _Right-click on formulas in this notebook to actually check it's rendered with Mathjax_.
(ns ^:nextjournal.clerk/no-cache mathjax
  (:require [nextjournal.clerk :as clerk]))

(clerk/set-viewers! [{:name :latex
                      :render-fn (quote v/mathjax-viewer)
                      :fetch-fn (fn [_ x] x)}])

;; Check **inline formulas** are rendered correctly in _deeper fragments_
;;
;; - this is an inline formula $\phi$ in
;; - a list
;;
;; this is a block formula in a markdown comment fragment
;;
;; $$
;; \bigoplus_{\alpha<\omega}\iota_\alpha
;; $$
;; while the following one is a block formula rendererd as clerk result

(clerk/tex "\\int_a^b\\varphi(t)dt")

;; equation references is only supported in MathJax

(clerk/tex "\\begin{equation}
 \\cos \\theta_1 = \\cos \\theta_2 \\implies \\theta_1 = \\theta_2
 \\label{eq:cosinjective}
 \\tag{COS-INJ}
 \\end{equation}")

;; this is an example $\eqref{eq:cosinjective}$.
