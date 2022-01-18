# 📝 Inline Results

```clojure
(ns ^:nextjournal.clerk/no-cache inline-results
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as v]))
```

```clojure
(defn red [text] (clerk/html [:strong [:em.ml-1.mr-1.underline.red text]]))
```

    Markdown monospace marks are actually evaluated `(red "this is some spacious red text")` inline.

Markdown monospace marks are actually evaluated `(red "this is some spacious red text")` inline.

```clojure
(defonce store (atom 0))
```
    
    This is inline `@store` and a `{:slider/min 0 :slider/max 10}` for ...

This is inline `@store` and a `{:slider/min 0 :slider/max 10 :slider/value @store}` for reactively changing values in-text.

```clojure
(defn reset-store! [n]
  (reset! store (read-string n))
  (clerk/show! "notebooks/inline_results.md"))
```

```clojure
(nextjournal.clerk/set-viewers!
  [{:pred #(and (map? %) (contains? % :slider/min) (contains? % :slider/max))
    :fetch-fn v/fetch-all
    :render-fn '(fn [{:as o :slider/keys [min max value]}]
                  (v/html
                   [:input {:type "range"
                            :default-value value
                            :min (str min)
                            :max (str max)
                            :on-change (fn [e] (v/clerk-eval (list 'reset-store! (.. e -target -value))))}]))}])
```

