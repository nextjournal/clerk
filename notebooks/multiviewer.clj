(ns multiviewer
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as viewer]
            [sicmutils.value :as v]
            [sicmutils.expression :as e]
            [sicmutils.env :refer :all]))

(defn ->formatted-str [expr]
  (let [form (v/freeze expr)]
    (with-out-str (e/print-expression form))))

(defn transform-literal [l]
  (let [simplified (simplify l)]
    {:simplified (-> simplified ->formatted-str clerk/code)
     :original (-> l ->formatted-str clerk/code)
     :TeX (-> l ->TeX clerk/tex)
     :simplified_TeX (-> simplified ->TeX clerk/tex)}))

#_(transform-literal (+ (square (sin 'x)) (square (cos 'x))))

(def literal-viewer
  {:pred e/literal?
   :fetch-fn viewer/fetch-all
   :transform-fn transform-literal
   :render-fn '(fn [x]
                 (v/html
                  (reagent/with-let [!sel (reagent/atom (-> x first key))]
                    [:<>
                     (into [:div.flex.items-center.font-sans.text-xs.mb-3
                            [:span.text-slate-500.mr-2 "View-as:"]]
                           (map (fn [[l _]]
                                  [:button.px-3.py-1.font-medium.hover:bg-indigo-50.rounded-full.hover:text-indigo-600.transition
                                   {:class (if (= @!sel l) "bg-indigo-100 text-indigo-600" "text-slate-500")
                                    :on-click #(reset! !sel l)}
                                   l]) x))
                     (get x @!sel)])))})

(clerk/set-viewers! [literal-viewer])

(+ (square (sin 'x))
   (square (cos 'x)))


(/ (+ (* 'A 'C 'gMR (expt (sin 'theta) 2) (cos 'theta))
      (* (/ 1 2) 'A (expt 'p_psi 2) (expt (sin 'theta) 2))
      (* (/ 1 2) 'C (expt 'p_psi 2) (expt (cos 'theta) 2))
      (* (/ 1 2) 'C (expt 'p_theta 2) (expt (sin 'theta) 2))
      (* -1 'C 'p_phi 'p_psi (cos 'theta))
      (* (/ 1 2) 'C (expt 'p_phi 2)))
   (* 'A 'C (expt (sin 'theta) 2)))
