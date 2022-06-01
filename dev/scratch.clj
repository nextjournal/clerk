(ns scratch
  (:require [nextjournal.clerk :as clerk]))

(clerk/with-viewers (clerk/add-viewers [{:pred number?
                                         :render-fn '(fn [v]
                                                       (do (js/console.log (goog.i18n.NumberFormat. (j/get-in goog.i18n.NumberFormat [:Format :COMPACT_SHORT])))
                                                           (js/console.log v)
                                                           v))}
                                        {:pred string?
                                         :render-fn '#(v/html [:p %])}])
  [1 "To begin at the beginning:"
   2 "It is Spring, moonless night in the small town, starless and bible-black,"
   3 "the cobblestreets silent and the hunched,"
   4 "courters'-and- rabbits' wood limping invisible"
   5 "down to the sloeblack, slow, black, crowblack, fishingboat-bobbing sea."])

