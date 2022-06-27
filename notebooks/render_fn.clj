(require '[nextjournal.clerk :as clerk])

#_(clerk/clear-cache!)

(clerk/with-viewers (clerk/add-viewers
                     [{:pred number?
                       :render-fn @(clerk/render-fn 'render-fn/heading)}
                      {:pred string?
                       :render-fn @(clerk/render-fn 'render-fn/paragraph)}])
  [1 "To begin at the beginning:"
   2 "It is Spring, moonless night in the small town, starless and bible-black,"
   3 "the cobblestreets silent and the hunched,"
   4 "courters'-and- rabbits' wood limping invisible"
   5 "down to the sloeblack, slow, black, crowblack, fishingboat-bobbing sea."])
