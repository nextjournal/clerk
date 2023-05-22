(ns nextjournal.clerk.cherry-env
  (:refer-clojure :exclude [time])
  (:require [applied-science.js-interop :as j]
            [cherry.embed :as cherry]
            [cljs.math]
            [cljs.reader]
            [clojure.string :as str]
            [goog.object]
            [nextjournal.clerk.parser]
            [nextjournal.clerk.render :as render]
            [nextjournal.clerk.render.code]
            [nextjournal.clerk.render.hooks]
            [nextjournal.clerk.render.navbar]
            [nextjournal.clerk.trim-image]
            [nextjournal.clerk.viewer :as viewer]
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

(declare eval-form)

(defn ->viewer-fn-with-error [form]
  (try (binding [*eval* eval-form]
         (viewer/->viewer-fn form))
       (catch js/Error e
         (viewer/map->ViewerFn
          {:form form
           :f (fn [_]
                [render/error-view (ex-info (str "error in render-fn: " (.-message e)) {:render-fn form} e)])}))))

(defn ->viewer-eval-with-error [form]
  (try (eval-form form)
       (catch js/Error e
         (js/console.error "error in viewer-eval" e)
         (ex-info (str "error in viewer-eval: " (.-message e)) {:form form} e))))

(defn ^:export cherry-compile-string [s]
  (cherry/compile-string
   s
   {:macros cherry-macros}))

(defn ^:export eval-form [f]
  (js/global-eval (cherry/compile-string
                   (str f)
                   {:macros cherry-macros})))
