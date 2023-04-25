(ns nextjournal.clerk.index
  {:nextjournal.clerk/visibility {:code :hide :result :hide}}
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as v]
            [nextjournal.clerk.builder :as builder]
            [babashka.fs :as fs]
            [clojure.edn :as edn]))

(defn paths-from-deps [deps-edn]
  (or (when-let [clerk-exec-args (get-in deps-edn [:aliases :nextjournal/clerk :exec-args])]
        (builder/expand-paths clerk-exec-args))
      {:error {:message "Please add a `:nextjournal/clerk` alias to your `deps.edn`."}}))


(def !paths
  (delay
    (if (fs/exists? "deps.edn")
      (paths-from-deps (edn/read-string (slurp "deps.edn")))
      {:error {:message "Could not find a `deps.edn`."}})))

(def index-item-viewer
  {:pred string?
   :transform-fn (clerk/update-val (fn [path]
                                     (clerk/html
                                      [:li.border-t.first:border-t-0.dark:border-gray-800.odd:bg-slate-50.dark:odd:bg-white
                                       {:class "dark:odd:bg-opacity-[0.03]"}
                                       [:a.pl-4.pr-4.py-2.flex.w-full.items-center.justify-between.hover:bg-indigo-50.dark:hover:bg-gray-700
                                        {:href (clerk/doc-url path)}
                                        [:span.text-sm.md:text-md.monospace.flex-auto.block.truncate path]
                                        [:svg.h-4.w-4.flex-shrink-0 {:xmlns "http://www.w3.org/2000/svg" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
                                         [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M9 5l7 7-7 7"}]]]])))})

(def index-viewer
  {:render-fn '(fn [xs opts]
                 [:div.not-prose
                  [:h1.mb-4 "Clerk"]
                  (into [:ul.border.dark:border-slate-800.rounded-md.overflow-hidden]
                        (nextjournal.clerk.render/inspect-children opts)
                        xs)])
   :transform-fn (fn [wrapped-value]
                   (update wrapped-value :nextjournal/viewers v/add-viewers [index-item-viewer]))})

{::clerk/visibility {:result :show}}

(clerk/with-viewer index-viewer @!paths)

