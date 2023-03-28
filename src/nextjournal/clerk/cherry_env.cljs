(ns nextjournal.clerk.cherry-env
  (:refer-clojure :exclude [time])
  (:require-macros [nextjournal.clerk.sci-env :refer [def-cljs-core]])
  (:require [applied-science.js-interop :as j]
            [cherry.compiler :as cherry]
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

(set! js/globalThis.clerk #js {})
(set! js/globalThis.clerk.cljs_core #js {})
(def-cljs-core)
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
  (let [{:keys [body _imports]}
        (cherry/compile-string*
         s
         {:core-alias 'clerk.cljs_core
          :context :expression
          :macros cherry-macros})]
    body))

(defn ^:export eval-form [f]
  (js/console.warn "compiling with cherry" (pr-str f))
  (let [{:keys [body _imports]}
        (cherry/compile-string*
         (str f)
         {:core-alias 'clerk.cljs_core
          :context :expression
          :macros cherry-macros})
        evaled (js/global_eval body)]
    evaled))
