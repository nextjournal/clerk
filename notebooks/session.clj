(ns session
  (:require [nextjournal.clerk :as clerk]))

^{::clerk/sync true}
(defonce ^:dynamic !offset
  (atom 0))

(clerk/col (clerk/with-viewer '(fn [_] [:div
                                        [:button.bg-sky-200.p-2.m-1.rounded {:on-click #(swap! !offset inc)} "inc"]
                                        [:button.bg-sky-200.p-2.m-1.rounded {:on-click #(swap! !offset dec)} "dec"]]) {})
           (clerk/vl
            {:data {:values [{"a" "A" "b" (+ @!offset 28)} {"a" "B" "b" 100} {"a" "C" "b" 43}
                             {"a" "D" "b" 91} {"a" "E" "b" 81} {"a" "F" "b" 53}
                             {"a" "G" "b" 19} {"a" "H" "b" 87} {"a" "I" "b" 52}]}
             :mark "bar"
             :encoding {"x" {"field" "a" "type" "nominal" "axis" {"labelAngle" 0}}
                        "y" {"field" "b" "type" "quantitative"}}
             :embed/opts {:actions false}}))


@!offset

(def offset
  @!offset)

(defn get-offset []
  @!offset)

(get-offset)

(inc offset)

