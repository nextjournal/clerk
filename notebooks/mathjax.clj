;; # MathJax
;; How to set default latex renderer to MathJax
(ns ^:nextjournal.clerk/no-cache mathjax
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as clerk-viewer]))

(clerk/set-viewers! [{:name :latex
                      :render-fn (quote v/mathjax-viewer)
                      :fetch-fn (fn [_ x] x)}])

;; this is **strong** _cursive_ text.
;;
;; - this is an inline formula $\phi$ in
;; - a list
;;
;; this is a block formula in markdown comment fragment
;;
;; $$
;; \bigoplus_{\alpha<\omega}\iota_\alpha
;; $$
;; while this is a block formula rendererd as clerk result

(clerk/tex "\\int_a^b\\varphi(t)dt")

;; this is only supported in MathJax

(clerk/tex "\\begin{equation}
 \\cos \\theta_1 = \\cos \\theta_2 \\implies \\theta_1 = \\theta_2
 \\label{eq:cosinjective}
 \\tag{COS-INJ}
 \\end{equation}")

;; As explained in $\eqref{eq:cosinjective}$.
