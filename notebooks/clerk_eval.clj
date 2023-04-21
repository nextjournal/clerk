;; # 📬 Replying to `clerk-eval`
(ns clerk-eval
  {:nextjournal.clerk/visibility {:code :fold}}
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as v]))

(clerk/with-viewer
  '(fn [code-str _]
     (let [!code (nextjournal.clerk.render.hooks/use-state code-str)
           !result (nextjournal.clerk.render.hooks/use-state nil)]
       [:div
        [:p "Enter a form to be evaluated using " [:code "v/clerk-eval"] ":"]
        [:div.flex.mb-4
         [:div.shadow-inner.border.rounded.bg-slate-100.px-2.py-1.flex-auto
          [nextjournal.clerk.render.code/editor !code]]
         [:button.rounded.px-3.py-1.bg-indigo-600.text-white.font-sans.font-bold.ml-2
          {:on-click #(reset! !result (nextjournal.clerk.render/clerk-eval (read-string @!code)))}
          "eval!"]]
        (when @!result
          [nextjournal.clerk.render/inspect @!result])]))
  "nextjournal.clerk.builder/clerk-docs")

;; We now get a reply from `v/clerk-eval`. To try it, press the `eval!` button above. ☝️
