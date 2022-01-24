(ns mathjax
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as clerk-viewer]))

#_(clerk/set-viewers! [{:name :latex :render-fn (quote v/mathjax-viewer) :fetch-fn (fn [_ x] x)}])
#_(reset! clerk-viewer/!viewers clerk-viewer/all-viewers)

(swap! clerk-viewer/!viewers update :root (partial mapv #(cond-> %
                                                           (= :latex (:name %))
                                                           (assoc :render-fn (quote v/mathjax-viewer)))))


(clerk/tex "\\begin{equation}
 \\cos \\theta_1 = \\cos \\theta_2 \\implies \\theta_1 = \\theta_2
 \\label{eq:cosinjective}
 \\tag{COS-INJ}
 \\end{equation}")


(clerk/tex "\\eqref{eq:cosinjective}")

;; As explained in $\eqref{eq:cosinjective}$.
