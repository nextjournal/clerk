;; # 👋 Hello CLJS

;; The following code is getting compiled by the ClojureScript compiler.

(.-location js/window)

;; Let's try using a viewer:
(viewer/html [:h3 "🤖 Hello Hiccup!"])

^{:nextjournal.clerk/visibility {:result :hide}} (+ 1 2 3)

;; Current limitations are:

;; * [ ] Can't use `ns` declaration
;; * [ ] Viewer functions need to be used either fully-qualified or using alias from `render.cljs`
;; * [ ] Need to trigger a recompilation after a change in the notebook
