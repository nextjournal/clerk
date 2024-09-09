(ns nextjournal.clerk.emmy
  (:require [reagent.core :as r]
            [nextjournal.clerk.render :as render]))

(defn render-literal [label->val]
  (r/with-let [!selected-label (r/atom (ffirst label->val))]
    [:<> (into
          [:div.flex.items-center.font-sans.text-xs.mb-3
           [:span.text-slate-500.mr-2 "View as:"]]
          (map (fn [label]
                 [:button.px-3.py-1.font-medium.hover:bg-indigo-50.rounded-full.hover:text-indigo-600.transition
                  {:class (if (= @!selected-label label) "bg-indigo-100 text-indigo-600" "text-slate-500")
                   :on-click #(reset! !selected-label label)}
                  label]))
          (keys label->val))
     [render/inspect-presented (get label->val @!selected-label)]]))
