;; # Customizing Viewers

;; Our goal with the Clerk viewer api is to _keep the toolbox open_
;; and let folks change both how things are displayed as well as how things behave. In this notebook, we'll go
;; through how the viewer api works and how you can change it.

(ns customizing-viewers
  (:require [clojure.string :as str]
            [nextjournal.clerk.viewer :as v]))

;; Clerk comes with a rich set of default viewers, and this is them.
v/default-viewers

;; Without a viewer specified, Clerk will go through the a sequence viewers and apply the `:pred` function in the viewer to find a matching one. Use `v/viewer-for` to select a viewer for a given value.
(def string?-viewer
  (v/viewer-for v/default-viewers "Denn wir sind wie Baumstämme im Schnee."))

;; Notice that for the `string?` viewer above, there's a `{:n 80}` on there. This is the case for all collection viewers in Clerk and controls how many elements are displayed. So using the default `string?-viewer` above, we're showing the first 80 characters.
(str/join (into [] cat (repeat 10 (range 10))))

;; If we change the viewer and set a different `:n` in `:fetch-opts`, we only see 10 characters.
(v/with-viewer (assoc-in string?-viewer [:fetch-opts :n] 10)
  (str/join (into [] cat (repeat 10 (range 10)))))

;; Or, we can turn off eliding, by dissoc'ing `:fetch-opts` alltogether.
(v/with-viewer (dissoc string?-viewer :fetch-opts)
  (str/join (into [] cat (repeat 10 (range 10)))))

;; The operations above were changes to a single viewer. But we also have a function `update-viewers` to update a given viewers by applying a `select-fn->update-fn` map. Here, the predicate is the keyword `:fetch-opts` and our update function is called for every viewer with `:fetch-opts` and is dissoc'ing them.
(def without-pagination
  {:fetch-opts #(dissoc % :fetch-opts)})

;; Here's the updated-viewers.
(def viewers-without-lazy-loading
  (v/update-viewers v/default-viewers without-pagination))

;; Now let's check what we got:
(filter :fetch-opts viewers-without-lazy-loading)

;; And compare it with the defaults:
(filter :fetch-opts v/default-viewers)

;; ### TODO:
;; * `v/with-viewers`
;; * `v/add-viewers`
;; * `:update-viewers-fn`
;; * selection based on predicates `(comp #{string?} :pred)`
;; * Setting Viewers per Namespace
;;   * `v/add-viewers!`
;;   * `v/reset-viewers!`
;; * Change the default viewers

