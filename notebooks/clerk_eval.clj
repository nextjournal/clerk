;; # 📬 Replying to `clerk-eval`
(ns clerk-eval
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.clerk.render.hooks :as-alias hooks]
            [nextjournal.clerk.viewer :as v]))

;; We first need a promise viewer:
(v/->viewer-eval '(.resolve js/Promise 42))

{:nextjournal.clerk/visibility {:code :fold}}

(clerk/with-viewer
  '(fn [code-str _]
     (let [!code (hooks/use-state code-str)
           !result (hooks/use-state nil)]
       [:div
        [:p "Enter a form to be evaluated using " [:code "v/clerk-eval"] ":"]
        [:div.flex.mb-4
         [:div.shadow-inner.border.rounded.bg-slate-100.px-2.py-1.flex-auto
          [nextjournal.clerk.render.code/editor !code]]
         [:button.rounded.px-3.py-1.bg-indigo-600.text-white.font-sans.font-bold.ml-2
          {:on-click #(reset! !result (v/clerk-eval (read-string @!code)))}
          "eval!"]]
        (when @!result
          [nextjournal.clerk.render/inspect @!result])]))
  "nextjournal.clerk.builder/clerk-docs")

