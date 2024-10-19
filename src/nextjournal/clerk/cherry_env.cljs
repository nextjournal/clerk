(ns nextjournal.clerk.cherry-env
  (:refer-clojure :exclude [time])
  (:require [applied-science.js-interop :as j]
            [cherry.embed :as cherry]
            [cljs.math]
            [cljs.reader]
            [clojure.string :as str]
            [goog.object]
            [nextjournal.clerk.parser]
            [nextjournal.clerk.render]
            [nextjournal.clerk.render.code]
            [nextjournal.clerk.render.hooks]
            [nextjournal.clerk.render.navbar]
            [nextjournal.clerk.trim-image]
            [nextjournal.clerk.viewer]
            [nextjournal.clojure-mode.commands]
            [nextjournal.clojure-mode.extensions.eval-region]
            [nextjournal.clojure-mode.keymap]
            [reagent.core :as reagent]
            [reagent.ratom :as ratom]
            [sci.configs.reagent.reagent :as sci.configs.reagent]))

(cherry/preserve-ns 'cljs.core)
(cherry/preserve-ns 'nextjournal.clerk.viewer)
(cherry/preserve-ns 'nextjournal.clerk.render)
(cherry/preserve-ns 'nextjournal.clerk.render.code)
(cherry/preserve-ns 'nextjournal.clerk.render.hooks)
(cherry/preserve-ns 'nextjournal.clerk.render.navbar)

(j/assoc-in! js/globalThis [:reagent :core :atom] reagent/atom)

(def reagent-ratom-namespace
  #js {:with-let-values ratom/with-let-values
       :reactive? ratom/reactive?
       :-ratom-context sci.configs.reagent/-ratom-context
       :atom reagent.ratom/atom
       :make-reaction reagent.ratom/make-reaction
       :make-track reagent.ratom/make-track
       :track! reagent.ratom/track!})

(defn munge-ns-obj [m]
  (.forEach (js/Object.keys m)
            (fn [k i]
              (unchecked-set m (munge k) (unchecked-get m k))
              (js-delete m k)))
  m)

(j/update-in! js/globalThis [:reagent :ratom] j/extend! (munge-ns-obj reagent-ratom-namespace))
(j/assoc! js/globalThis :global_eval (fn [x]
                                       (js/eval.apply js/globalThis #js [x])))

(def cherry-macros {'reagent.core {'with-let sci.configs.reagent/with-let}})

(defn ^:export cherry-compile-string [s]
  (cherry/compile-string
   s
   {:macros cherry-macros}))

(defn ^:export eval-form [f]
  (js/global-eval (cherry/compile-string
                   (str f)
                   {:macros cherry-macros})))
