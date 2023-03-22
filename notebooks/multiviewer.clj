(ns multiviewer
  (:require [clojure.walk :as w]
            [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as viewer]
            [sicmutils.env :refer :all]
            [sicmutils.expression :as e]
            [sicmutils.value :as v]))

(defn ->formatted-str [expr]
  (let [form (v/freeze expr)]
    (with-out-str (e/print-expression form))))

(defn transform-literal [literal]
  {"TeX" (-> literal ->TeX clerk/tex)
   "Original" (-> literal ->formatted-str clerk/code)
   "Simplified" (-> literal simplify ->formatted-str clerk/code)
   "Simplified TeX" (-> literal simplify ->TeX clerk/tex)})

(def literal-viewer
  {:pred e/literal?
   :transform-fn (comp clerk/mark-preserve-keys
                       (clerk/update-val transform-literal))
   :render-fn '(fn [label->val]
                 (reagent.core/with-let [!selected-label (reagent.core/atom (ffirst label->val))]
                   [:<> (into
                         [:div.flex.items-center.font-sans.text-xs.mb-3
                          [:span.text-slate-500.mr-2 "View-as:"]]
                         (map (fn [label]
                                [:button.px-3.py-1.font-medium.hover:bg-indigo-50.rounded-full.hover:text-indigo-600.transition
                                 {:class (if (= @!selected-label label) "bg-indigo-100 text-indigo-600" "text-slate-500")
                                  :on-click #(reset! !selected-label label)}
                                 label]))
                         (keys label->val))
                    [nextjournal.clerk.render/inspect (get label->val @!selected-label)]]))})

(clerk/add-viewers! [literal-viewer])

(+ (square (sin 'x))
   (square (cos 'x)))


(/ (+ (* 'A 'C 'gMR (expt (sin 'theta) 2) (cos 'theta))
      (* (/ 1 2) 'A (expt 'p_psi 2) (expt (sin 'theta) 2))
      (* (/ 1 2) 'C (expt 'p_psi 2) (expt (cos 'theta) 2))
      (* (/ 1 2) 'C (expt 'p_theta 2) (expt (sin 'theta) 2))
      (* -1 'C 'p_phi 'p_psi (cos 'theta))
      (* (/ 1 2) 'C (expt 'p_phi 2)))
   (* 'A 'C (expt (sin 'theta) 2)))
