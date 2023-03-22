;; # 🎛 Control Lab 🧑🏼‍🔬
(ns viewers.control-lab
  {:nextjournal.clerk/visibility {:code :hide :result :hide}}
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as viewer]
            [nextjournal.clerk.experimental :as cx]))


;; Let's go through the ways we can use this.

{:nextjournal.clerk/visibility {:code :show :result :show}}

;; 1️⃣ On a var coming from a def
^{::clerk/sync true ::clerk/viewer cx/slider}
(defonce !num (atom 0))


#_(reset! !num 50)

;; 2️⃣ On a sharp quoted symbol (works with a fully qualified one as well, ofc).
(cx/slider `!num)


;; 3️⃣ On an explicit `ViewerEval` type
(cx/slider (viewer/->viewer-eval `!num))


#_ ;; TODO: 4️⃣ plain (not quoted) symbol
(slider !num)

;; We can customise the slider by passing different opts (that are merged).

(cx/slider {:max 10 :step 0.002} `!num)

;; Or use a completely custom `:render-fn`.
(clerk/with-viewer
  (assoc viewer/viewer-eval-viewer
         :render-fn
         '(fn [x]
            [:div.inline-flex.items-center.bg-green-400.rounded-xl.px-2.text-xl.select-none.gap-1
             [:span.cursor-pointer {:on-click #(swap! x dec)} "⏮"]
             [:div.font-bold.font-sans.p-2.text-white.w-32.text-center @x]
             [:span.cursor-pointer {:on-click #(swap! x inc)} "⏭"]
             [:span.cursor-pointer {:on-click #(reset! x 0)} "⏹"]]))
  `!num)

;; This is the default viewer for a `ViewerEval` type.

(clerk/with-viewer viewer/viewer-eval-viewer `!num)


;; This is the value from the JVM.

@!num


^{::clerk/sync true ::clerk/viewer cx/text-input}
(defonce !greeting (atom "Hello, Friend 👋"))

@!greeting
