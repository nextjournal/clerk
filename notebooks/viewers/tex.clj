;; # TeX 🧮
;; ## With KateX and MathJax
^{:nextjournal.clerk/visibility {:code :hide}}
(ns tex
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as v]))

;; The Einstein-Field Equations are:
(clerk/tex "G_{\\mu\\nu}\\equiv R_{\\mu\\nu} - {\\textstyle 1 \\over 2}R\\,g_{\\mu\\nu} = {8 \\pi G \\over c^4} T_{\\mu\\nu}")

;; Formulas should render correctly both inline $e^{2\pi i}-1 = 0$ as well as in blocks
;;
;; $${\frac{d}{d t} \frac{∂ L}{∂ \dot{q}}}-\frac{∂ L}{∂ q}=0.$$
;;
;; also called _display mode_ in $\LaTeX$. The same should render fine in markdown results:

(clerk/md "Inline $e^{2\\pi i}-1 = 0$ and block

$${\\frac{d}{d t} \\frac{∂ L}{∂ \\dot{q}}}-\\frac{∂ L}{∂ q}=0.$$")

;; ## Errors
;; This is a parse error in prose $\phi\crash$ while this is in a result

(clerk/tex "\\phi\\crash")

;; ## MathJax

(v/with-viewer v/mathjax-viewer
  "G_{\\mu\\nu}\\equiv R_{\\mu\\nu} - {\\textstyle 1 \\over 2}R\\,g_{\\mu\\nu} = {8 \\pi G \\over c^4} T_{\\mu\\nu}")
