(ns nextjournal.clerk.squint-env
  (:refer-clojure :exclude [time])
  (:require [applied-science.js-interop :as j]
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
            [sci.configs.reagent.reagent :as sci.configs.reagent]
            [squint.compiler :as squint]))

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
  (let [js-str (:body (squint/compile-string*
                       (str f) {:context :expr
                                :core-alias 'cljs.core}))]
    (prn :js-str js-str)
    (js/global-eval js-str {:macros cherry-macros})))
