;; # TeX 🧮
;; ## With KateX and MathJax
(ns tex (:require [nextjournal.clerk :as clerk]))

;; The Einstein-Field Equations are:
(clerk/tex "G_{\\mu\\nu}\\equiv R_{\\mu\\nu} - {\\textstyle 1 \\over 2}R\\,g_{\\mu\\nu} = {8 \\pi G \\over c^4} T_{\\mu\\nu}")
