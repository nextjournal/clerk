(ns nextjournal.clerk.analytics
  (:require [nextjournal.devcards :as dc]
            [nextjournal.clerk.viewer :as v]
            [nextjournal.clerk.sci-viewer :refer [inspect]]))

(defn namespaces-list [namespaces]
  (into [:ul.text-sm]
        (map (fn [{:keys [name namespaces]}]
               [:li
                [:a.text-indigo-700.hover:underline {:href "#"} name]
                (when (seq namespaces)
                  [:div.ml-3
                   [namespaces-list namespaces]])])
             namespaces)))

(dc/defcard show-ns
  [state]
  (let [{:keys [namespaces selected-namespace]} @state]
    [:div.flex.h-screen
     [:div.border-r.overflow-y-auto.p-6.flex-shrink-0
      {:style {:width 200}}
      [:div.text-xs.text-gray-500.uppercase.tracking-wide.mb-2 "Namespaces"]
      [namespaces-list namespaces]]
     (when-let [{:keys [vars]} selected-namespace]
       [:div.border-r.overflow-y-auto.p-6.flex-shrink-0
        {:style {:width 200}}
        [:div.text-xs.text-gray-500.uppercase.tracking-wide.mb-2 "Vars"]
        (into [:ul.text-sm]
              (map (fn [{:keys [name]}]
                     [:li [:a.text-indigo-700.hover:underline {:href "#"} name]])
                   (:vars selected-namespace)))])
     (when-let [{:keys [name docstring vars]} selected-namespace]
       [:div.flex-auto.overflow-y-auto.p-6.text-sm
        [:div.max-w-6xl.mx-auto
         [:h1.text-2xl name]
         [:div.mt-4.leading-normal.viewer-markdown
          [inspect (v/md docstring)]]
         (into
           [:div]
           (map (fn [{:keys [name docstring examples]}]
                  [:div.border-t-2.mt-6.pt-6
                   [:h2.text-lg name]
                   [:div.mt-4.viewer-markdown
                    [inspect (v/md docstring)]]
                   (into [:div [:div.text-xs.text-gray-500.uppercase.tracking-wide.mt-4.mb-2 "Examples"]]
                         (map (fn [{:keys [code]}]
                                [:div
                                 [inspect (v/code code)]])
                              examples))])
                vars))]
        ])])
  {::dc/class "p-0"
   ::dc/state {:namespaces [{:name "pattern"
                             :namespaces [{:name "consequence"}
                                          {:name "match"}
                                          {:name "rule"}
                                          {:name "syntax"}]}]
               :selected-namespace {:name "pattern.match"
                                    :docstring "Implementation of a pattern matching system inspired by Gerald Jay Sussman's lecture notes for MIT 6.945. See `pattern.rule` for a higher-level API.\n\n`pattern.match` and `pattern.rule` are spiritually similar to Alexey Radul's Rules library for Scheme, and the pattern matching system described in GJS and Hanson's Software Design for Flexibility.\n\n"
                                    :vars [{:name "all-results"
                                            :docstring "Convenience function that creates an all-results-matcher from the supplied pattern and immediately applies it to data."
                                            :examples [{:code "(all-results pattern data)"}
                                                       {:code "((all-results-matcher pattern pred) data)"}]}
                                           {:name "all-results-matcher"
                                            :docstring "Takes a pattern and callback function f, and returns a matcher that takes a data argument and returns a sequence of every possible match of pattern to the data.\n\nFor a convenience function that applies the matcher to data immediately, see all-results.\n\nNOTE: If you pass a segment matcher, f must accept two arguments - the binding map, and the sequence of all remaining items that the segment matcher rejected."
                                            :examples [{:code "(all-results-matcher pattern)"}]}
                                           {:name "and"
                                            :docstring "Takes a sequence of patterns and returns a matcher that will apply its arguments to the first pattern;\n\nIf that match succeeds, the next pattern will be called with the new, returned frame (and the original data and success continuation).\n\nThe returned matcher succeeds only of all patterns succeed, and returns the value of the final pattern."
                                            :examples [{:code "(and)"}
                                                       {:code "(and pattern)"}
                                                       {:code "(and pattern & more)"}]}]}}})

