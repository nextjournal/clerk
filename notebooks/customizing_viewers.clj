;; # Customizing Viewers

;; Our goal with the Clerk viewer api is to _keep the toolbox open_
;; and let folks change both how things are displayed as well as how things behave. In this notebook, we'll go
;; through how the viewer api works and how you can change it.

(ns customizing-viewers
  (:require [clojure.string :as str]
            [nextjournal.clerk.viewer :as v]))

;; Clerk comes with a rich set of default viewers, and this is them.
v/default-viewers

;; A Clerk viewer is just a Clojure map. Let's start with a very basic example.
(def greeting-viewer
  {:render-fn '(fn [name] (v/html [:strong "Hello, " name "!"]))})

;; In it's simplest form, a viewer has just a `:render-fn`. Notice that the value is not yet a function, but a quoted form that will be sent via a websocket to the browser. There, it will be evaluated using the [Small Clojure Intepreter](https://github.com/babashka/sci) or SCI for short. Let's use the viewer to confirm it does what we expect:
(v/with-viewer greeting-viewer
  "James Clerk Maxwell")

;; It is often useful, but not a neccesity to define a viewer in a clojure var, so the following expression yields the same result.
(v/with-viewer {:render-fn '(fn [name] (v/html [:strong "Hello, " name "!"]))}
  "James Clerk Maxwell")

;; Besides `:render-fn`, there's also a part of the viewer api, that runs directly in JVM Clojure, `:transform-fn`.
;; We can use it do archieve the same thing:
(v/with-viewer {:transform-fn (fn [wrapped-value]
                                (v/html [:strong "Hello, " (v/->value wrapped-value) "!"]))}
  "James Clerk Maxwell")

;; Note that this _is_ a function, not a quoted form like `:render-fn`. It does not recieve the plain value, but it's value is wrapped in a map under the `:nextjournal/value` key which allows it to carry and convey additional information.

;; Let's use `v/apply-viewers` to look more closely at what this does:
#_ "TODO: remove equality hack once we can display wrapped-values as-is." 
(= (-> (v/with-viewer {:transform-fn (fn [wrapped-value]
                                       (v/html [:strong "Hello, " (v/->value wrapped-value) "!"]))}
         "James Clerk Maxwell")
       (v/apply-viewers)
       (v/process-wrapped-value))
   {:nextjournal/viewer {:name :html, :render-fn (v/->ViewerFn 'v/html)},
    :nextjournal/value [:strong "Hello, " "James Clerk Maxwell" "!"]})

;; Without a viewer specified, Clerk will go through the a sequence viewers and apply the `:pred` function in the viewer to find a matching one. Use `v/viewer-for` to select a viewer for a given value.
(def char?-viewer
  (v/viewer-for v/default-viewers \A))

(def string?-viewer
  (v/viewer-for v/default-viewers "Denn wir sind wie Baumstämme im Schnee."))

;; Notice that for the `string?` viewer above, there's a `{:n 80}` on there. This is the case for all collection viewers in Clerk and controls how many elements are displayed. So using the default `string?-viewer` above, we're showing the first 80 characters.
(def long-string
  (str/join (into [] cat (repeat 10 (range 10)))))

;; If we change the viewer and set a different `:n` in `:page-size`, we only see 10 characters.
(v/with-viewer (assoc string?-viewer :page-size 10)
  long-string)

;; Or, we can turn off eliding, by dissoc'ing `:page-size` alltogether.
(v/with-viewer (dissoc string?-viewer :page-size)
  long-string)

;; The operations above were changes to a single viewer. But we also have a function `update-viewers` to update a given viewers by applying a `select-fn->update-fn` map. Here, the predicate is the keyword `:page-size` and our update function is called for every viewer with `:page-size` and is dissoc'ing them.
(def without-pagination
  {:page-size #(dissoc % :page-size)})

;; Here's the updated-viewers:
(def viewers-without-lazy-loading
  (v/update-viewers v/default-viewers without-pagination))

;; Now let's confirm these modified viewers don't have `:page-size` on them anymore.
(filter :page-size viewers-without-lazy-loading)

;; And compare it with the defaults:
(filter :page-size v/default-viewers)




;; ### TODO:
;; * `v/with-viewers`
;; * `v/add-viewers`
;; * `:update-viewers-fn`
;; * selection based on predicates `(comp #{string?} :pred)`
;; * Setting Viewers per Namespace
;;   * `v/add-viewers!`
;;   * `v/reset-viewers!`
;; * Change the default viewers



