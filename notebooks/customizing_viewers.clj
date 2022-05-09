;; # Customizing Viewers

;; Our goal with the Clerk viewer api is to _keep the toolbox open_
;; and let folks change both how things are displayed as well as how things behave. In this notebook, we'll go
;; through how the viewer api works and how you can change it.

(ns customizing-viewers
  (:require [nextjournal.clerk.viewer :as v]))

;; Clerk comes with a rich set of default viewers, and this is them.
v/default-viewers

;; Without a viewer specified, Clerk will go through the a sequence viewers and apply the `:pred` function in the viewer to find a matching one.
(def string?-viewer
  (v/viewer-for v/default-viewers "Denn wir sind wie Baumstämme im Schnee."))

;; Notice that for the `string?` viewer above, there's a `{:n 100}` on there. This is the case for all collection viewers in Clerk and controls how many elements are displayed.
(v/with-viewer (assoc-in string?-viewer [:fetch-opts :n] 85)
  (clojure.string/join (into [] cat (repeat 10 (range 10)))))

(v/with-viewer (dissoc string?-viewer :fetch-opts)
  (clojure.string/join (into [] cat (repeat 10 (range 10)))))

;; You can see Clerk is performing pagination as to not overload the browser with too much data. Since we're not dealing with a huge amount of data here, let's turn that off.
(def without-pagination
  {:fetch-opts #(dissoc % :fetch-opts)})

(def viewers-without-lazy-loading
  (v/update-viewers v/default-viewers {:fetch-opts #(dissoc % :fetch-opts)}))

(def update-dropping-lazy-loading
  (partial v/update-viewers {:fetch-opts #(dissoc % :fetch-opts)}))

;; ## Pagination
(filter :fetch-opts v/default-viewers)
