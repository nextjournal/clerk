(ns devtools
  (:require [devtools.core :as devtools]))

;; invert cljs devtools colors to keep things readable when dark mode is on
(when (and (exists? js/window.matchMedia)
           (.. js/window (matchMedia "(prefers-color-scheme: dark)") -matches))
  (let [{:keys [cljs-land-style]} (devtools/get-prefs)]
    (devtools/set-pref! :cljs-land-style (str "filter:invert(1);" cljs-land-style))))
