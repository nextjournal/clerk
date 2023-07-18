(ns nextjournal.clerk.squint-env
  (:refer-clojure :exclude [time])
  (:require [cljs.math]
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
            [sci.configs.reagent.reagent :as sci.configs.reagent]
            [cherry.embed :as cherry]
            [squint.compiler :as squint]
            [shadow.esm :as esm]
            ["/squint/core" :as squint-core]
            ))

(set! (.-squint_core js/globalThis) squint-core)

(js/console.log squint-core)

#_(-> (esm/dynamic-import "squint/core.js")
    (.catch (fn [err]
              (js/console.log "err" err))))

(cherry/preserve-ns 'clojure.string)

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

(defn ^:export squint-compile-string [s]
  (squint/compile-string s))

(def cherry-macros {'reagent.core {'with-let sci.configs.reagent/with-let}})

(defn ^:export eval-form [f]
  (prn :squint-expr f)
  (let [js-str (:body (squint/compile-string*
                       (str f) {:context :expr
                                :core-alias 'squint_core
                                :macros cherry-macros}))]
    (prn :squint-js-str js-str)
    (js/global-eval js-str)))
