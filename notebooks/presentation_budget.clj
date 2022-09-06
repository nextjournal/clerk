;; # Presentation Budget
(ns presentation-budget
  (:require [nextjournal.clerk :as clerk]))

;; To not overload the browser, Clerk will paginate sequences. In addition to this pagination behaviour, there's a per-result limit in place, the _presentation budget_.

;; You can change this budget per-namespace.
^::clerk/no-cache
(clerk/reset-viewers! (clerk/update-viewers
                       (clerk/get-default-viewers)
                       {:presentation-budget #(assoc % :presentation-budget 250)}))


(clerk/get-default-viewers)

;; Or set a different one using metadata on the form.
^{::clerk/presentation-budget 1000}
(clerk/get-default-viewers)


