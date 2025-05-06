(ns nextjournal.clerk.render.context
  "The following provide and consume Reagent components expose React contexts without fuss.
  * Contexts can be keywords or React context instances. In the case of keywords, React context instances are created behind the scenes.
  * Context values are left alone, they remain as JS or Clojure values (no coercion).
  * Ratoms inside consume work as you'd expect.
  * You can provide multiple contexts at the same time, but you can only c/consume one context at a time.
  example use:
  ``` clojure
  [c/provide {:app-theme {:color \"blue\"}}
   ;; consume one context at a time
   [c/consume :app-theme
    (fn [{:keys [color] :as _theme}]
      [:div {:style {:color color}} \"Colorful Text\"])]]
  ```
  From https://gist.github.com/mhuebert/d400701f7eddbc4fffa811c70178a8c1"
  (:require ["react" :as react]
            [reagent.core :as reagent]))

(defonce get-context
  (memoize
   (fn [k]
     (if (keyword? k)
       (react/createContext (munge (str k)))
       k))))

(defn provide
  "Adds React contexts to the component tree.
   `bindings` should be a map of {<keyword-or-Context>, <value-to-be-bound>}."
  [bindings & body]
  (loop [bindings (seq bindings)
         out (->> body
                  (reduce (fn [a el] (doto a (.push (reagent/as-element el)))) #js [])
                  (.concat #js [react/Fragment #js {}])
                  (.apply react/createElement nil))]
    (if (empty? bindings)
      out
      (recur (rest bindings)
             (let [[context-or-key v] (first bindings)
                   ^js context (get-context context-or-key)]
               (react/createElement (.-Provider context)
                                    #js {:value v}
                                    out))))))

(defn consume
  "Reads a React context value within component tree.
   `context` should be a keyword or React Context instance."
  [context f]
  (react/createElement
   (.-Consumer (get-context context))
   #js {}
   (fn [value]
     (reagent/as-element [f value]))))
