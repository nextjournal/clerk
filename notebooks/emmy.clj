(ns emmy-repro
  (:require [emmy.env :as e]
            [emmy.mechanics.lagrange]))

;; ## BUG 1:

;; This notebook takes close to 2 seconds to evaluate:

;; Clerk evaluated '/Users/sritchie/code/clj/clerk-demo/notebooks/emmy_repro.clj' in 1853.674042ms.

;; For the final Langrangian in generalized coordinates (the angles of each
;; segment) by composing `L-rect` with a properly transformed `angles->rect`
;; coordinate transform!

;; ## BUG 2:
;;
;; The following form:

#_
(let [L (emmy.mechanics.lagrange/L-pendulum 'g 'm 'l)]
  (((e/Lagrange-equations L)
    (e/literal-function 'theta_1))
   't))

;; Evaluates to this:
(e/literal-number
 '(- (* 1/2 m 2 l (((expt D 2) theta_1) t) l) (* g m l (- (sin (theta_1 t))))))


;; But if I include it in a notebook, I get this:

;; Execution error (NullPointerException) at clojure.tools.analyzer.jvm.utils/members* (utils.clj:272).
;; Cannot invoke "java.lang.Class.getName()" because the return value of "clojure.lang.IFn.invoke(Object)" is null
