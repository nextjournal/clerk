(ns nextjournal.clerk.render
  (:require ["framer-motion" :refer [motion]]
            ["react" :as react]
            ["react-dom/client" :as react-client]
            [applied-science.js-interop :as j]
            [cljs.reader]
            [clojure.set :as set]
            [clojure.string :as str]
            [editscript.core :as editscript]
            [goog.events :as gevents]
            [goog.object]
            [goog.string :as gstring]
            [nextjournal.clerk.render.code :as code]
            [nextjournal.clerk.render.context :as view-context]
            [nextjournal.clerk.render.hooks :as hooks]
            [nextjournal.clerk.render.navbar :as navbar]
            [nextjournal.clerk.render.panel :as panel]
            [nextjournal.clerk.viewer :as viewer]
            [nextjournal.clerk.walk :as w]
            [reagent.core :as r]
            [reagent.ratom :as ratom]
            [sci.core :as sci]
            [sci.ctx-store]
            [shadow.cljs.modern :refer [defclass]]))

(r/set-default-compiler! (r/create-compiler {:function-components true}))

(declare inspect inspect-presented html html-viewer)

(def nbsp (gstring/unescapeEntities "&nbsp;"))

(defonce !state
  (r/atom {:eval-counter 0
           :doc nil
           :viewers viewer/!viewers
           :panels {}
           :render-errors []}))

(defonce !eval-counter (r/cursor !state [:eval-counter]))
(defonce !doc (r/cursor !state [:doc]))
(defonce !viewers (r/cursor !state [:viewers]))
(defonce !panels (r/cursor !state [:panels]))
(defonce !render-errors (r/cursor !state [:render-errors]))

(defn reagent-atom? [x]
  (satisfies? ratom/IReactiveAtom x))

(defn dark-mode-toggle []
  (let [spring {:type :spring :stiffness 200 :damping 10}]
    [:div.relative.dark-mode-toggle
     [:button.text-slate-400.hover:text-slate-600.dark:hover:text-white.cursor-pointer
      {:on-click #(swap! code/!dark-mode? not)}
      (if @code/!dark-mode?
        [:> (.-svg motion)
         {:xmlns "http://www.w3.org/2000/svg"
          :class "w-5 h-5 md:w-4 md:h-4"
          :viewBox "0 0 50 50"
          :key "moon"}
         [:> (.-path motion)
          {:d "M 43.81 29.354 C 43.688 28.958 43.413 28.626 43.046 28.432 C 42.679 28.238 42.251 28.198 41.854 28.321 C 36.161 29.886 30.067 28.272 25.894 24.096 C 21.722 19.92 20.113 13.824 21.683 8.133 C 21.848 7.582 21.697 6.985 21.29 6.578 C 20.884 6.172 20.287 6.022 19.736 6.187 C 10.659 8.728 4.691 17.389 5.55 26.776 C 6.408 36.163 13.847 43.598 23.235 44.451 C 32.622 45.304 41.28 39.332 43.816 30.253 C 43.902 29.96 43.9 29.647 43.81 29.354 Z"
           :fill "currentColor"
           :initial "initial"
           :animate "animate"
           :variants {:initial {:scale 0.6 :rotate 90}
                      :animate {:scale 1 :rotate 0 :transition spring}}}]]
        [:> (.-svg motion)
         {:key "sun"
          :class "w-5 h-5 md:w-4 md:h-4"
          :viewBox "0 0 24 24"
          :fill "none"
          :xmlns "http://www.w3.org/2000/svg"}
         [:> (.-circle motion)
          {:cx "11.9998"
           :cy "11.9998"
           :r "5.75375"
           :fill "currentColor"
           :initial "initial"
           :animate "animate"
           :variants {:initial {:scale 1.5}
                      :animate {:scale 1 :transition spring}}}]
         [:> (.-g motion)
          {:initial "initial"
           :animate "animate"
           :variants {:initial {:rotate 45}
                      :animate {:rotate 0 :transition spring}}}
          [:circle {:cx "3.08982" :cy "6.85502" :r "1.71143" :transform "rotate(-60 3.08982 6.85502)" :fill "currentColor"}]
          [:circle {:cx "3.0903" :cy "17.1436" :r "1.71143" :transform "rotate(-120 3.0903 17.1436)" :fill "currentColor"}]
          [:circle {:cx "12" :cy "22.2881" :r "1.71143" :fill "currentColor"}]
          [:circle {:cx "20.9101" :cy "17.1436" :r "1.71143" :transform "rotate(-60 20.9101 17.1436)" :fill "currentColor"}]
          [:circle {:cx "20.9101" :cy "6.8555" :r "1.71143" :transform "rotate(-120 20.9101 6.8555)" :fill "currentColor"}]
          [:circle {:cx "12" :cy "1.71143" :r "1.71143" :fill "currentColor"}]]])]]))

(defn exec-status [{:keys [progress cell-progress status]}]
  [:<>
   [:div.w-full.bg-purple-200.dark:bg-purple-900.rounded.z-20 {:class "h-[2px]"}
    [:div.bg-purple-600.dark:bg-purple-400 {:class "h-[2px]" :style {:width (str (* progress 100) "%")}}]
    [:div.absolute.text-purple-600.dark:text-white.text-xs.font-sans.ml-1.bg-white.dark:bg-purple-900.rounded-full.shadow.z-20.font-bold.px-2.border.border-slate-300.dark:border-purple-400
     {:style {:font-size "0.5rem"} :class "left-[35px] md:left-0 mt-[7px] md:mt-1"}
     status]]
   (when cell-progress
     [:div.w-full.bg-sky-100.dark:bg-purple-900.rounded.z-20 {:class "h-[2px] mt-[0.5px]"}
      [:div.bg-sky-500.dark:bg-purple-400 {:class "h-[2px]" :style {:width (str (* cell-progress 100) "%")}}]])])

(defn connection-status [status]
  [:div.absolute.text-red-600.dark:text-white.text-xs.font-sans.ml-1.bg-white.dark:bg-red-800.rounded-full.shadow.z-30.font-bold.px-2.border.border-red-400
   {:style {:font-size "0.5rem"} :class "left-[35px] md:left-0 mt-[7px] md:mt-1"}
   status])

(declare inspect-children)

(defn closest-anchor-parent [^js el]
  (loop [el el]
    (when el
      (if (= "A" (.-nodeName el))
        el
        (recur (.-parentNode el))))))

(declare clerk-eval)

(defn scroll-to-location-hash! []
  (when-some [heading (when (exists? js/location)
                        (try (some-> js/location .-hash not-empty js/decodeURI (subs 1) js/document.getElementById)
                             (catch js/Error _
                               (js/console.warn (str "Clerk render-notebook, invalid hash: "
                                                     (.-hash js/location))))))]
    (js/requestAnimationFrame #(.scrollIntoView heading))))

(defn render-notebook [{xs :blocks :keys [package doc-css-class sidenotes? toc toc-visibility header footer]}
                       {:as render-opts :keys [!expanded-at]}]
  (hooks/use-effect #(swap! !expanded-at merge (navbar/->toc-expanded-at toc toc-visibility)) [toc toc-visibility])
  (let [!mobile-toc? (hooks/use-state (navbar/mobile?))
        root-ref-fn (hooks/use-callback (fn [el]
                                          (when (and el (exists? js/document))
                                            (code/setup-dark-mode!)
                                            (when (not= :single-file package)
                                              (scroll-to-location-hash!)))))]
    [:div.flex
     {:ref root-ref-fn}
     [:div.fixed.top-2.left-2.md:left-auto.md:right-2.z-10
      [dark-mode-toggle]]
     (when (and toc toc-visibility)
       (let [render-opts' (assoc render-opts :!mobile-toc? !mobile-toc?)]
         [navbar/container render-opts'
          [inspect-presented render-opts' toc]]))
     [:div.flex-auto.w-screen.scroll-container
      (into
       [:> (.-div motion)
        (merge
         {:key "notebook-viewer"
          :class (cond-> (or doc-css-class [:flex :flex-col :items-center :notebook-viewer :flex-auto])
                   sidenotes? (conj :sidenotes-layout))}
         (when (and toc (not (navbar/mobile?)))
           (let [inset {:margin-left (if (and toc-visibility (:toc-open? @!expanded-at)) navbar/width 0)}]
             {:initial inset
              :animate inset
              :transition navbar/spring})))]
       ;; TODO: restore react keys via block-id
       ;; ^{:key (str processed-block-id "@" @!eval-counter)}
       (inspect-children render-opts) (concat (when header [header]) xs (when footer [footer])))]]))

(defn opts->query [opts]
  (->> opts
       (map #(update % 0 name))
       (map (partial str/join "="))
       (str/join "&")))


#_(opts->query {:s 12 :num 42})



(defn render-unreadable-edn [edn]
  [:span.inspected-value.whitespace-nowrap.cmt-default edn])

(defn error-badge [& content]
  [:div.bg-red-50.rounded-sm.text-xs.text-red-400.px-2.py-1.items-center.sans-serif.inline-flex
   [:svg.h-4.w-4.text-red-400 {:xmlns "http://www.w3.org/2000/svg" :viewBox "0 0 20 20" :fill "currentColor" :aria-hidden "true"}
    [:path {:fill-rule "evenodd" :d "M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z" :clip-rule "evenodd"}]]
   (into [:div.ml-2.font-bold] content)])

(defn error-view [error]
  (let [!stack-expanded (hooks/use-state false)]
    [:div.bg-red-100.dark:bg-gray-800.px-6.py-4.rounded-md.text-xs.dark:border-2.dark:border-red-300.not-prose
     [:p.font-mono.text-red-600.dark:text-red-300.font-bold (or (:message error) (.-message error))]
     (when-let [data (or (:data error) (.-data error))]
       [:<>
        (when-let [extra-view (::extra-view data)]
          [:div.mt-2.overflow-auto [inspect extra-view]])
        [:div.mt-2.overflow-auto [inspect (dissoc data ::extra-view)]]])
     (when-let [stack (try
                        (->> (or (:stack error) (.-stack error))
                             str/split-lines
                             (drop 1)
                             (mapv str/trim))
                        (catch js/Error _ nil))]
       [:pre.text-red-600.dark:text-red-300.w-full.overflow-auto.mt-2 {:class "text-[11px] max-h-[155px]"}
        [:span.underline.cursor-pointer {:on-click #(swap! !stack-expanded not)}
         (if @!stack-expanded "Hide" "Show")
         " Stacktrace (" (count stack) " lines)\n"]
        (when @!stack-expanded
          (str/join "\n" stack))])]))

(defclass ErrorBoundary
  (extends react/Component)
  (field handle-error)
  (field hash)
  (constructor [this ^js props]
               (super props)
               (set! (.-state this) #js {:error nil :hash (j/get props :hash)})
               (set! hash (j/get props :hash))
               (set! handle-error (fn [error]
                                    (set! (.-state this) #js {:error error}))))

  Object
  (render [this ^js props]
          (j/let [^js {{:keys [error]} :state
                       {:keys [children]} :props} this]
            (if error
              (r/as-element [error-view error])
              children))))

(j/!set ErrorBoundary
        :getDerivedStateFromError (fn [error] #js {:error error})
        :getDerivedStateFromProps (fn [props state]
                                    (when (not= (j/get props :hash)
                                                (j/get state :hash))
                                      #js {:hash (j/get props :hash) :error nil})))


(def default-loading-view "Loading...")

;; TODO: drop this
(defn read-string [s]
  (js/nextjournal.clerk.sci_env.read-string s))


(defn fetch! [{:keys [blob-id]} opts]
  #_(js/console.log :fetch! blob-id opts)
  (-> (js/fetch (str "/_blob/" blob-id (when (seq opts)
                                         (str "?" (opts->query opts)))))
      (.then #(.text %))
      (.then #(try (read-string %)
                   (catch js/Error e
                     (js/console.error #js {:message "sci read error" :blob-id blob-id :code-string % :error e})
                     (render-unreadable-edn %))))))

(defn ->expanded-at [auto-expand? presented]
  (cond-> presented
    auto-expand? (-> viewer/assign-content-lengths)
    true (-> viewer/assign-expanded-at (get :nextjournal/expanded-at {}))))

(defn result-css-class [x]
  (let [{viewer-name :name} (viewer/->viewer x)
        viewer-css-class (viewer/css-class x)
        inner-viewer-name (some-> x viewer/->value viewer/->viewer :name)]
    (if viewer-css-class
      (cond-> viewer-css-class
        (string? viewer-css-class) vector)
      ["viewer"
       (when (get-in x [:nextjournal/render-opts :fragment-item?]) "fragment-item")
       (when viewer-name (name viewer-name))
       (when inner-viewer-name (name inner-viewer-name))
       (case (or (viewer/width x)
                 (case viewer-name
                   (`viewer/code-viewer) :wide
                   (`viewer/markdown-node-viewer) :nested-prose
                   :prose))
         :wide "w-full max-w-wide"
         :full "w-full"
         :nested-prose "w-full max-w-prose"
         "w-full max-w-prose px-8")])))

(defn render-result [{:nextjournal/keys [fetch-opts hash presented]} {:keys [id auto-expand-results?]}]
  (let [!desc (hooks/use-state-with-deps presented [hash])
        !expanded-at (hooks/use-state-with-deps (when (map? @!desc) (->expanded-at auto-expand-results? @!desc)) [hash])
        fetch-fn (hooks/use-callback (when fetch-opts
                                       (fn [opts]
                                         (.then (fetch! fetch-opts opts)
                                                (fn [more]
                                                  (swap! !desc viewer/merge-presentations more opts)
                                                  (swap! !expanded-at #(merge (->expanded-at auto-expand-results? @!desc) %))))))
                                     [hash])
        on-key-down (hooks/use-callback (fn [event]
                                          (if (.-altKey event)
                                            (swap! !expanded-at assoc :prompt-multi-expand? true)
                                            (swap! !expanded-at dissoc :prompt-multi-expand?))))
        on-key-up (hooks/use-callback #(swap! !expanded-at dissoc :prompt-multi-expand?))
        ref-fn (hooks/use-callback #(if %
                                      (when (exists? js/document)
                                        (js/document.addEventListener "keydown" on-key-down)
                                        (js/document.addEventListener "keyup" on-key-up))
                                      (when (exists? js/document)
                                        (js/document.removeEventListener "keydown" on-key-down)
                                        (js/document.removeEventListener "up" on-key-up))))]
    (when @!desc
      [view-context/provide {:fetch-fn fetch-fn}
       [:> ErrorBoundary {:hash hash}
        [:div.result-viewer {:class (result-css-class @!desc) :data-block-id id :ref ref-fn}
         [:div.relative
          [:div.overflow-x-auto
           {:ref ref-fn}
           [inspect-presented {:!expanded-at !expanded-at} @!desc]]]]]])))

(defn toggle-expanded [!expanded-at path event]
  (.preventDefault event)
  (.stopPropagation event)
  (let [{:keys [hover-path prompt-multi-expand?]} @!expanded-at
        hover-path-count (count hover-path)
        hover-path-expanded? (get @!expanded-at path)]
    (if (and hover-path prompt-multi-expand? (= (count path) hover-path-count))
      (swap! !expanded-at (fn [expanded-at]
                            (reduce
                             (fn [acc [path expanded?]]
                               (if (and (coll? path) (vector? path) (= (count path) hover-path-count))
                                 (assoc acc path (not hover-path-expanded?))
                                 (assoc acc path expanded?)))
                             {}
                             expanded-at)))
      (swap! !expanded-at update path not))))


(defn expandable? [xs]
  (< 1 (count xs)))

(defn inspect-children [opts]
  (map (fn [x] (cond-> [inspect-presented opts x]
                 (get-in x [:nextjournal/render-opts :id])
                 (with-meta {:key (str (get-in x [:nextjournal/render-opts :id]) "@" @!eval-counter)})))))

(def expand-style
  ["cursor-pointer"
   "bg-indigo-50"
   "hover:bg-indigo-100"
   "border-b"
   "border-gray-400"
   "hover:border-gray-500"
   "dark:bg-gray-900"
   "dark:hover:bg-slate-700"
   "dark:border-slate-600"
   "dark:hover:border-slate-500"])

(defn triangle [expanded?]
  [:svg {:viewBox "0 0 100 100"
         :class (str "w-[7px] h-[7px] fill-current inline-block transition-all mr-[1px] -mt-[2px] "
                     (if expanded? "rotate-180" "rotate-90"))}
   [:polygon {:points "5.9,88.2 50,11.8 94.1,88.2 "}]])

(def triangle-spacer [:span {:class "inline-block w-[8px]"}])

(defn expand-button [!expanded-at opening-paren path]
  (let [expanded? (get @!expanded-at path)
        {:keys [hover-path prompt-multi-expand?]} @!expanded-at
        multi-expand? (and hover-path prompt-multi-expand? (= (count path) (count hover-path)))]
    [:span.group.hover:bg-indigo-100.rounded-sm.hover:shadow.cursor-pointer
     {:class (when multi-expand? "bg-indigo-100 shadow ")
      :on-click (partial toggle-expanded !expanded-at path)
      :on-mouse-enter #(swap! !expanded-at assoc :hover-path path)
      :on-mouse-leave #(swap! !expanded-at dissoc :hover-path)}
     [:span.text-slate-400.group-hover:text-indigo-700
      {:class (when multi-expand? "text-indigo-700 ")}
      [triangle expanded?]]
     [:span.group-hover:text-indigo-700 opening-paren]]))

(defn render-coll [xs {:as opts :keys [path viewer !expanded-at] :or {path []}}]
  (let [expanded? (get @!expanded-at path)
        {:keys [opening-paren closing-paren]} viewer]
    [:span.inspected-value.whitespace-nowrap
     {:class (when expanded? "inline-flex")}
     [:span
      (if (< 1 (count xs))
        [expand-button !expanded-at opening-paren path]
        [:span opening-paren])
      (into [:<>]
            (comp (inspect-children opts)
                  (interpose (if expanded? [:<> [:br] triangle-spacer nbsp (when (= 2 (count opening-paren)) nbsp)] " ")))
            xs)
      [:span
       (cond->> closing-paren (list? closing-paren) (into [:<>]))]]]))

(defn render-elision [{:as fetch-opts :keys [total offset unbounded?]} _]
  [view-context/consume :fetch-fn
   (fn [fetch-fn]
     [:span.sans-serif.relative.whitespace-nowrap
      {:style {:border-radius 2 :padding (when (fn? fetch-fn) "1px 3px") :font-size 11 :top -1}
       :class (if (fn? fetch-fn)
                "cursor-pointer bg-indigo-200 hover:bg-indigo-300 dark:bg-gray-700 dark:hover:bg-slate-600 text-gray-900 dark:text-white"
                "text-gray-400 dark:text-slate-300")
       :on-click #(when (fn? fetch-fn)
                    (fetch-fn fetch-opts))} (- total offset) (when unbounded? "+") (if (fn? fetch-fn) " more…" " more elided")])])

(defn render-map [xs {:as opts :keys [path viewer !expanded-at] :or {path []}}]
  (let [expanded? (get @!expanded-at path)
        {:keys [closing-paren]} viewer]
    [:span.inspected-value.whitespace-nowrap
     {:class (when expanded? "inline-flex")}
     [:span
      (if (expandable? xs)
        [expand-button !expanded-at "{" path]
        [:span "{"])
      (into [:<>]
            (comp (inspect-children opts)
                  (interpose (if expanded? [:<> [:br] triangle-spacer nbsp #_(repeat (inc (count path)) nbsp)] " ")))
            xs)
      (cond->> closing-paren (list? closing-paren) (into [:<>]))]]))


(defn render-string [s {:as opts :keys [path !expanded-at] :or {path []}}]
  (let [expanded? (get @!expanded-at path)]
    (into [:span.whitespace-pre]
          (map #(if (string? %)
                  (if expanded?
                    (into [:<>] (interpose [:<> [:br]] (str/split-lines %)))
                    (into [:<>] (interpose [:span.text-slate-400 "↩︎"] (str/split-lines %))))
                  (inspect-presented opts %)))
          (if (string? s) [s] s))))

(defn render-quoted-string [s {:as opts :keys [path viewer !expanded-at] :or {path []}}]
  (let [{:keys [opening-paren closing-paren]} viewer]
    [:span.inspected-value.inline-flex
     [:span.cmt-string
      (if (some #(and (string? %) (str/includes? % "\n")) (if (string? s) [s] s))
        [expand-button !expanded-at opening-paren path]
        [:span opening-paren])]
     [:div
      [:span.cmt-string (viewer/->value (render-string s opts)) (first closing-paren)]
      (when (list? closing-paren) (into [:<>] (rest closing-paren)))]]))

(defn render-number [num]
  [:span.cmt-number.inspected-value
   (if (js/Number.isNaN num) "NaN" (str num))])

(defn sort! [!sort i k]
  (let [{:keys [sort-key sort-order]} @!sort]
    (reset! !sort {:sort-index i
                   :sort-key k
                   :sort-order (if (= sort-key k) (if (= sort-order :asc) :desc :asc) :asc)})))

(defn sort-data [{:keys [sort-index sort-order]} {:as data :keys [head rows]}]
  (cond-> data
    head (assoc :rows (->> rows
                           (sort-by #(cond-> (get % sort-index)
                                       (string? val) str/lower-case)
                                    (if (= sort-order :asc) #(compare %1 %2) #(compare %2 %1)))
                           vec))))

(def eye-icon
  [:svg {:width "15"
         :height "15"
         :viewBox "0 0 15 15"
         :fill "none"
         :xmlns "http://www.w3.org/2000/svg"}
   [:path
    {:d "M7.5 11C4.80285 11 2.52952 9.62184 1.09622 7.50001C2.52952 5.37816 4.80285 4 7.5 4C10.1971 4 12.4705 5.37816 13.9038 7.50001C12.4705 9.62183 10.1971 11 7.5 11ZM7.5 3C4.30786 3 1.65639 4.70638 0.0760002 7.23501C-0.0253338 7.39715 -0.0253334 7.60288 0.0760014 7.76501C1.65639 10.2936 4.30786 12 7.5 12C10.6921 12 13.3436 10.2936 14.924 7.76501C15.0253 7.60288 15.0253 7.39715 14.924 7.23501C13.3436 4.70638 10.6921 3 7.5 3ZM7.5 9.5C8.60457 9.5 9.5 8.60457 9.5 7.5C9.5 6.39543 8.60457 5.5 7.5 5.5C6.39543 5.5 5.5 6.39543 5.5 7.5C5.5 8.60457 6.39543 9.5 7.5 9.5Z"
     :fill "currentColor"
     :fill-rule "evenodd"
     :clip-rule "evenodd"}]])

(def eye-closed-icon
  [:svg {:width "15"
         :height "15"
         :viewBox "0 0 15 15"
         :fill "none"
         :xmlns "http://www.w3.org/2000/svg"}
   [:path
    {:d "M14.7649 6.07596C14.9991 6.22231 15.0703 6.53079 14.9239 6.76495C14.4849 7.46743 13.9632 8.10645 13.3702 8.66305L14.5712 9.86406C14.7664 10.0593 14.7664 10.3759 14.5712 10.5712C14.3759 10.7664 14.0593 10.7664 13.8641 10.5712L12.6011 9.30817C11.805 9.90283 10.9089 10.3621 9.93375 10.651L10.383 12.3277C10.4544 12.5944 10.2961 12.8685 10.0294 12.94C9.76267 13.0115 9.4885 12.8532 9.41704 12.5865L8.95917 10.8775C8.48743 10.958 8.00036 10.9999 7.50001 10.9999C6.99965 10.9999 6.51257 10.958 6.04082 10.8775L5.58299 12.5864C5.51153 12.8532 5.23737 13.0115 4.97064 12.94C4.7039 12.8686 4.5456 12.5944 4.61706 12.3277L5.06625 10.651C4.09111 10.3621 3.19503 9.90282 2.3989 9.30815L1.1359 10.5712C0.940638 10.7664 0.624058 10.7664 0.428798 10.5712C0.233537 10.3759 0.233537 10.0593 0.428798 9.86405L1.62982 8.66303C1.03682 8.10643 0.515113 7.46742 0.0760677 6.76495C-0.0702867 6.53079 0.000898544 6.22231 0.235065 6.07596C0.469231 5.9296 0.777703 6.00079 0.924058 6.23496C1.40354 7.00213 1.989 7.68057 2.66233 8.2427C2.67315 8.25096 2.6837 8.25972 2.69397 8.26898C4.00897 9.35527 5.65537 9.99991 7.50001 9.99991C10.3078 9.99991 12.6564 8.5063 14.076 6.23495C14.2223 6.00079 14.5308 5.9296 14.7649 6.07596Z"
     :fill "currentColor"
     :fill-rule "evenodd"
     :clip-rule "evenodd"}]])

(defn throwable-view [{:as _error :keys [via trace]} opts]
  (let [!stack-expanded? (nextjournal.clerk.render.hooks/use-state false)
        !caused-by-expanded? (nextjournal.clerk.render.hooks/use-state false)
        unhandled (first via)]
    [:div.bg-red-100.text-sm.w-full.border-t.border-red-200.overflow-auto.font-mono
     {:style {:max-height "60vh"}}
     [:div.px-5.py-4.border-t.border-red-200.first:border-t-0
      (when (:type unhandled)
        [:div.font-bold.text-red-600 "Unhandled " (:type unhandled)])
      [:div.font-bold.mt-1 (:message unhandled)]
      (when (:data unhandled)
        [:div.mt-1
         [nextjournal.clerk.render/inspect (:data unhandled)]])]
     (when-let [caused-by (not-empty (rest via))]
       [:div.border-t.border-red-200.text-xs.px-5.py-2.first:border-t-0
        [:div.text-xs.text-red-600.font-bold.hover:underline.cursor-pointer.flex.items-center.gap-2
         {:on-click #(swap! !caused-by-expanded? not)}
         (if @!caused-by-expanded? eye-icon eye-closed-icon)
         (if @!caused-by-expanded? "Hide" "Show") " causes (" (count caused-by) ")"]
        (when @!caused-by-expanded?
          (into [:div]
                (map
                 (fn [{:as _ex :keys [type message data _trace]}]
                   [:div.px-5.mt-3 {:class "ml-[1px]"}
                    (when type
                      [:div.font-bold.text-red-600 "Caused by " type])
                    [:div.font-bold.mt-1 message]
                    (when data
                      [:div.mt-1
                       [nextjournal.clerk.render/inspect data]])]))
                caused-by))])
     (when trace
       [:div.border-t.border-red-200.text-xs.px-5.py-2.first:border-t-0
        [:div.text-xs.text-red-600.font-bold.hover:underline.cursor-pointer.flex.items-center.gap-2
         {:on-click #(swap! !stack-expanded? not)}
         (if @!stack-expanded? eye-icon eye-closed-icon)
         (if @!stack-expanded? "Hide" "Show") " stacktrace"]
        (when @!stack-expanded?
          [:table.w-full.not-prose
           (into [:tbody]
                 (map (fn [[call _x file line]]
                        [:tr.hover:bg-red-100.leading-tight
                         [:td.text-right.px-6 file ":"]
                         [:td.text-right.pr-6 line]
                         [:td.py-1.pr-6 call]]))
                 trace)])])]))

(defn render-throwable [ex opts]
  (if (or (:stack ex) (instance? js/Error ex))
    [error-view ex]
    [:div.rounded.border.border-red-200.border-t-0.overflow-hidden
     [throwable-view ex opts]]))

(defn render-tagged-value
  ([tag value] (render-tagged-value {:space? true} tag value))
  ([{:keys [space?]} tag value]
   [:span.inspected-value.whitespace-nowrap
    [:span.cmt-meta tag] (when space? nbsp) value]))

(defn set-viewers! [scope viewers]
  #_(js/console.log :set-viewers! {:scope scope :viewers viewers})
  (swap! !viewers assoc scope (vec viewers))
  'set-viewers!)

(declare default-viewers)

(defn valid-react-element? [x] (react/isValidElement x))

(defn inspect-presented
  ([x]
   (r/with-let [!expanded-at (r/atom (:nextjournal/expanded-at x))]
     [inspect-presented {:!expanded-at !expanded-at} x]))
  ([opts x]
   (if (valid-react-element? x)
     x
     (let [{:nextjournal/keys [value viewer] :keys [path]} x
           hash (str (:hash viewer) "@" (peek (:path opts)))]
       ;; each view function must be called in its own 'functional component' so that it gets its own hook state.
       ^{:key hash}
       [:> ErrorBoundary {:hash hash}
        [(:render-fn viewer) value (merge opts
                                          (:nextjournal/render-opts x)
                                          {:viewer viewer :path path})]]))))

(defn inspect [value]
  (r/with-let [!state   (r/atom nil)
               fetch-fn (fn [fetch-opts]
                          (let [{:keys [present-elision-fn]} (-> !state deref :desc meta)]
                            (-> js/Promise
                                (.resolve (present-elision-fn fetch-opts))
                                (.then
                                 (fn [more]
                                   (swap! !state update :desc viewer/merge-presentations more fetch-opts))))))]
    (when (not= (:value @!state ::not-found) value)
      (swap! !state assoc
             :value value
             :desc (viewer/present value)))
    [view-context/provide {:fetch-fn fetch-fn}
     [inspect-presented (:desc @!state)]]))

(defn show-panel [panel-id panel]
  (swap! !panels assoc panel-id panel))

#_(show-panel :test {:content [:div "Test"] :width 600 :height 600})

(defn with-fetch-fn [{:nextjournal/keys [presented blob-id]} body-fn]
  ;; TODO: unify with result-viewer
  (let [!presented-value (hooks/use-state presented)
        body-fn* (hooks/use-callback body-fn)
        fetch-fn (hooks/use-callback
                  (fn [elision]
                    (-> (fetch! {:blob-id blob-id} elision)
                        (.then (fn [more]
                                 (swap! !presented-value viewer/merge-presentations more elision))))))]
    [view-context/provide
     {:fetch-fn fetch-fn}
     [body-fn* @!presented-value]]))

(defn exception-overlay [title & content]
  (into
   [:div.fixed.bottom-0.left-0.font-mono.w-screen.z-20
    [:div.text-4xl.absolute.left-1
     {:style {:transform "rotate(-15deg)"
              :text-shadow "0 2px 5px rgba(0,0,0,.1)"
              :z-index 1
              :top -5}}
     (rand-nth ["😩" "😬" "😑" "😖"])]
    [:div.flex.ml-7
     [:div.pl-4.pr-3.pt-1.rounded-t.bg-red-100.text-red-600.text-sm.font-bold.relative.border-t.border-l.border-r.border-red-200
      {:style {:bottom -1}}
      title]]]
   content))

(defn render-errors-overlay [errors]
  [exception-overlay
   "Render Errors"
   (into [:div]
         (map (fn [e]
                [throwable-view e]))
         errors)])

(defn clojure-exception-overlay [presented-value]
  (let [!expanded-at (r/atom {})]
    [exception-overlay
     "Errors"
     [inspect-presented {:!expanded-at !expanded-at} presented-value]]))

(defn root []
  [:> ErrorBoundary {:hash @!doc}
   [:div.fixed.w-full.z-20.top-0.left-0.w-full
    (when-let [status (:nextjournal.clerk.sci-env/connection-status @!doc)]
      [connection-status status])
    (when-let [status (:status @!doc)]
      [exec-status status])]
   (if-let [{:as wrapped-value :nextjournal/keys [blob-id]} (get-in @!doc [:nextjournal/value :error])]
     ^{:key blob-id} [with-fetch-fn wrapped-value clojure-exception-overlay]
     (when-let [render-errors (not-empty @!render-errors)]
       [render-errors-overlay render-errors]))
   (when (:nextjournal/value @!doc)
     [inspect-presented @!doc])
   (into [:<>]
         (map (fn [[id state]]
                ^{:key id}
                [panel/show
                 (:content state)
                 (-> state
                     (assoc :id id :on-close #(swap! !panels dissoc id)))]))
         @!panels)])

(declare mount)

(defonce ^:private ^:dynamic *sync* true)

(defn ws-send! [msg]
  (if (exists? js/ws_send)
    (js/ws_send (pr-str msg))
    (js/console.warn "Clerk can't send websocket message in static build, skipping...")))

(defn atom-changed [var-name _atom old-state new-state]
  (when *sync*
    (ws-send! {:type :sync!
               :var-name var-name
               :patch (editscript/get-edits (editscript/diff old-state new-state {:algo :quick}))})))

(defn intern-atom! [var-name state]
  (assert (sci.ctx-store/get-ctx) "sci-ctx must be set")
  (sci/intern (sci.ctx-store/get-ctx)
              (sci/create-ns (symbol (namespace var-name)))
              (symbol (name var-name))
              (doto (r/atom state)
                (add-watch var-name atom-changed))))


(defonce ^:private !synced-atom-vars
  (atom #{}))

(defn sci-ns-unmap! [ns-sym var-sym]
  (let [ns-unmap (sci/eval-string* (sci.ctx-store/get-ctx) "ns-unmap")]
    (ns-unmap ns-sym var-sym)))

(defonce ^:dynamic *reset-sync-atoms?* true)
(defn set-reset-sync-atoms! [new-val] (set! *reset-sync-atoms?* new-val))

(defn intern-atoms! [atom-var-name->state]
  (let [vars-in-use (into #{} (keys atom-var-name->state))
        vars-interned @!synced-atom-vars]
    (doseq [var-name-to-unmap (set/difference vars-interned vars-in-use)]
      (sci-ns-unmap! (symbol (namespace var-name-to-unmap)) (symbol (name var-name-to-unmap))))
    (doseq [[var-name value] atom-var-name->state]
      (if-let [existing-var (sci/resolve (sci.ctx-store/get-ctx) var-name)]
        (when *reset-sync-atoms?*
          (binding [*sync* false]
            (reset! @existing-var value)))
        (intern-atom! var-name value)))
    (reset! !synced-atom-vars vars-in-use)))

(defn remount? [doc-or-patch]
  (true? (some #(= % :nextjournal.clerk/remount) (tree-seq coll? seq doc-or-patch))))

(defn re-eval-render-fns [doc]
  ;; TODO: `intern-atoms!` is currently called twice in case of a
  ;; remount patch-state! event
  (intern-atoms! (-> doc :nextjournal/value :atom-var-name->state))
  (let [re-eval (fn [{:keys [form]}] (viewer/->render-fn form))]
    (w/postwalk (fn [x] (cond-> x (and (viewer/render-fn? x) (not (:eval x))) re-eval)) doc)))

(defn eval-cljs-evals [doc]
  (reset! !render-errors [])
  (intern-atoms! (-> doc :nextjournal/value :atom-var-name->state))
  (w/postwalk (fn [x]
                (if (viewer/render-eval? x)
                  (try (deref (:f x))
                       (catch js/Error e
                         (js/console.error "error in render-eval" e (:form x))
                         (swap! !render-errors conj (Throwable->map e))))
                  x))
              doc))

(defn run-effects! [effects]
  (doseq [effect effects]
    (deref (:f effect))))

(defn ^:export set-state! [{:as state :keys [doc effects]}]
  (run-effects! effects)
  (when (contains? state :doc)
    (when (exists? js/window)
      ;; TODO: can we restore the scroll position when navigating back?
      (.scrollTo js/window #js {:top 0}))
    (reset! !doc (eval-cljs-evals doc)))
  ;; (when (and error (contains? @!doc :status))
  ;;   (swap! !doc dissoc :status))
  (when (remount? doc)
    (swap! !eval-counter inc))
  (when-let [title (and (exists? js/document) (-> doc viewer/->value :title))]
    (set! (.-title js/document) title)))

(defn apply-patch [x patch]
  (eval-cljs-evals (editscript/patch x (editscript/edits->script patch))))

(defn patch-state! [{:keys [patch effects]}]
  (run-effects! effects)
  (if (remount? patch)
    (do (swap! !doc #(re-eval-render-fns (apply-patch % patch)))
        ;; TODO: figure out why it doesn't work without `js/setTimeout`
        (js/setTimeout #(swap! !eval-counter inc) 10))
    (swap! !doc apply-patch patch)))

(defonce !pending-clerk-eval-replies
  (atom {}))

(defn clerk-eval
  ([form] (clerk-eval {} form))
  ([{:keys [recompute?]} form]
   (let [eval-id (gensym)
         promise (js/Promise. (fn [resolve reject]
                                (swap! !pending-clerk-eval-replies assoc eval-id {:resolve resolve :reject reject})))]
     (ws-send! {:type :eval :form form :eval-id eval-id :recompute? (boolean recompute?)})
     promise)))

(defn process-eval-reply! [{:keys [eval-id reply error]}]
  (if-let [{:keys [resolve reject]} (get @!pending-clerk-eval-replies eval-id)]
    (do (swap! !pending-clerk-eval-replies dissoc eval-id)
        (if error
          (do (swap! !render-errors conj error)
              (reject error))
          (resolve reply)))
    (js/console.warn :process-eval-reply!/not-found :eval-id eval-id :keys (keys @!pending-clerk-eval-replies))))

(defonce container-el
  (and (exists? js/document) (js/document.getElementById "clerk")))

(defonce hydrate?
  (and container-el
       (pos? (.-childElementCount container-el))))

(defonce react-root
  (when container-el
    (if hydrate?
      (react-client/hydrateRoot container-el (r/as-element [root]))
      (react-client/createRoot container-el))))

(defonce !router (atom nil))

(defn ->URL [^js href]
  (js/URL. href))

(defn path-from-url-hash [url]
  (-> url ->URL .-hash (subs 2)))

(defn ->doc-url [url]
  (let [path (js/decodeURI (.-pathname url))
        doc-path (js/decodeURI (.-pathname (.-location js/document)))]
    (if (str/starts-with? path doc-path)
      (subs path (count doc-path))
      (subs path 1))))

(defn ignore-anchor-click?
  [e ^js url]
  (let [[current-origin current-path] (when (exists? js/location)
                                        [(.-origin js/location) (.-pathname js/location)])
        ^js dataset (some-> e .-target closest-anchor-parent .-dataset)]
    (or (not= current-origin (.-origin url))
        (= current-path (.-pathname url))
        (.-altKey e)
        (some-> dataset .-ignoreAnchorClick some?))))

(defn history-push-state [{:as opts :keys [path fragment replace?]}]
  (when (not= path (some-> js/history .-state .-path))
    (j/call js/history (if replace? :replaceState :pushState) (clj->js opts) "" (str (.. js/document -location -origin)
                                                                                     "/" path (when fragment (str "#" fragment))))))

(defn handle-history-popstate [^js e]
  (when-some [path (:path (js->clj (.-state e) :keywordize-keys true))]
    (.preventDefault e)
    (clerk-eval (list 'nextjournal.clerk.webserver/navigate! {:nav-path path :skip-history? true}))))

(defn handle-hashchange [{:keys [url->path path->doc]} ^js e]
  ;; used for navigation in static bundle build
  (let [url (some-> e .-event_ .-newURL path-from-url-hash)]
    (when-some [doc (get path->doc url)]
      (set-state! {:doc doc}))))

(defn handle-anchor-click [^js e]
  (when-some [url (some-> e .-target closest-anchor-parent .-href not-empty ->URL)]
    (when-not (ignore-anchor-click? e url)
      (.preventDefault e)
      (clerk-eval (list 'nextjournal.clerk.webserver/navigate!
                        (cond-> {:nav-path (->doc-url url)}
                          (seq (.-hash url))
                          (assoc :fragment (subs (.-hash url) 1))))))))

(defn handle-initial-load [^js _e]
  (history-push-state {:path (subs js/location.pathname 1) :replace? true}))

(defn utf8-decode [bytes]
  (.decode (js/TextDecoder. "utf-8") bytes))

(defn delay-resolve [v] (new js/Promise (fn [res] (js/setTimeout #(res v) 100))))

(defn read-response+show-progress [{:as state :keys [reader buffer content-length]}]
  (swap! !doc assoc :status {:progress (if (zero? (count buffer)) 0.2 (/ (count buffer) content-length))})
  (.. reader read
      ;; delay a bit for progress bar to be visible
      (then delay-resolve)
      (then (fn [ret]
              (if (.-done ret)
                buffer
                (read-response+show-progress (update state :buffer str (utf8-decode (.-value ret)))))))))

(defn fetch+set-state [edn-path]
  (.. ^js (js/fetch edn-path)
      (then (fn handle-response [r]
              (if (.-ok r)
                {:buffer ""
                 :reader (.. r -body getReader)
                 :content-length (js/Number. (.. r -headers (get "content-length")))}
                (throw (ex-info (.-statusText r) {:url (.-url r)
                                                  :status (.-status r)
                                                  :headers (.-headers r)})))))
      (then read-response+show-progress)
      (then (fn [edn]
              (set-state! {:doc (read-string edn)}) {:ok true}))
      (catch (fn [e] (js/console.error "Fetch failed" e)
               (set-state! {:doc {:nextjournal/viewer {:render-fn (constantly [:<>])} ;; FIXME: make :error top level on state
                                  :nextjournal/value {:error (viewer/present e)}}})
               {:ok false :error e}))))

(defn click->fetch [e]
  (when-some [url (some-> ^js e .-target closest-anchor-parent .-href not-empty ->URL)]
    (when-not (ignore-anchor-click? e url)
      (.preventDefault e)
      (let [path (.-pathname url)
            edn-path (str path (when (str/ends-with? path "/") "index") ".edn")]
        (.pushState js/history #js {:edn_path edn-path} ""
                    (str (cond-> path
                           (not (str/ends-with? path "/"))
                           (str "/")) (.-hash url))) ;; a trailing slash is needed to make relative paths work
        (fetch+set-state edn-path)))))

(defn load->fetch [{:keys [current-path]} _e]
  ;; TODO: consider fixing this discrepancy via writing EDN one step deeper in directory
  (let [edn-path (-> (.-pathname js/document.location)
                     (str/replace #"/(index.html)?$" "")
                     (cond->
                       (empty? current-path)
                       (str "/index.edn")
                       (seq current-path)
                       (str ".edn")))]
    (.pushState js/history #js {:edn_path edn-path} "" nil)
    (fetch+set-state edn-path)))

(defn popstate->fetch [^js e]
  (when-some [edn-path (when (.-state e) (.. e -state -edn_path))]
    (.preventDefault e)
    (fetch+set-state edn-path)))

(defn setup-router! [{:as state :keys [render-router]}]
  (when (and (exists? js/document) (exists? js/window))
    (doseq [listener (:listeners @!router)]
      (gevents/unlistenByKey listener))
    (reset! !router
            (when render-router
              (assoc state :listeners
                     (case render-router
                       :bundle
                       [(gevents/listen js/window gevents/EventType.HASHCHANGE (partial handle-hashchange state) false)]
                       :fetch-edn
                       [(gevents/listen js/document gevents/EventType.CLICK click->fetch false)
                        (gevents/listen js/window gevents/EventType.POPSTATE popstate->fetch false)
                        (gevents/listen js/window gevents/EventType.LOAD (partial load->fetch state) false)]
                       :serve
                       [(gevents/listen js/document gevents/EventType.CLICK handle-anchor-click false)
                        (gevents/listen js/window gevents/EventType.POPSTATE handle-history-popstate false)
                        (gevents/listen js/window gevents/EventType.LOAD handle-initial-load false)]))))))


(defn ^:export mount []
  (when (and react-root (not hydrate?))
    (.render react-root (r/as-element [root]))))

(defn ^:dev/after-load ^:after-load re-render []
  (swap! !doc re-eval-render-fns)
  (mount))

(defn ^:export init [{:as state :keys [render-router path->doc]}]
  (setup-router! state)
  (when (contains? #{:bundle :serve} render-router)
    (set-state! (case render-router
                  :bundle {:doc (get path->doc (or (path-from-url-hash (->URL (.-href js/location))) ""))}
                  :serve state)))
  (mount))


(defn render-html [markup]
  (r/as-element (if (string? markup)
                  [:span {:dangerouslySetInnerHTML (r/unsafe-html markup)}]
                  markup)))

(defn render-promise [p opts]
  (let [!state (hooks/use-state {:pending true})]
    (hooks/use-effect (fn []
                        (-> p
                            (.then #(reset! !state {:value %}))
                            (.catch #(reset! !state {:error %})))))
    (let [{:keys [pending value error]} @!state]
      (if pending
        default-loading-view
        [inspect (or pending value error)]))))


(defn with-d3-require [{:keys [package loading-view]
                        :or {loading-view default-loading-view}} f]
  (if-let [package (hooks/use-d3-require package)]
    (f package)
    loading-view))

(defn with-dynamic-import [{:keys [module loading-view]
                            :or {loading-view default-loading-view}} f]
  (if-let [package (hooks/use-dynamic-import module)]
    (f package)
    loading-view))

(defn render-vega-lite [value]
  (let [handle-error (hooks/use-error-handler)
        vega-embed (hooks/use-d3-require "vega-embed@6.11.1")
        opts (get value :embed/opts {})
        ref-fn (react/useCallback #(when %
                                     (-> (.embed vega-embed
                                                 %
                                                 (clj->js (dissoc value :embed/opts :embed/callback))
                                                 (clj->js opts))
                                         (.then (fn [result] (if-let [callback (:embed/callback value)]
                                                               (callback result)
                                                               result)))
                                         (.catch handle-error)))
                                  #js[value vega-embed])]
    (when value
      (if vega-embed
        [:div.overflow-x-auto
         [:div.vega-lite {:ref ref-fn}]]
        default-loading-view))))

(defn render-plotly [value]
  (let [plotly (hooks/use-d3-require "plotly.js-dist@2.15.1")
        ref-fn (react/useCallback #(when %
                                     (.react plotly % (clj->js value)))
                                  #js[value plotly])]
    (when value
      (if plotly
        [:div.overflow-x-auto
         [:div.plotly {:ref ref-fn}]]
        default-loading-view))))

(defn render-katex [tex-string {:keys [inline?]}]
  (let [katex (hooks/use-d3-require "katex@0.16.4")]
    (if katex
      [:span {:dangerouslySetInnerHTML (r/unsafe-html (.renderToString katex tex-string (j/obj :displayMode (not inline?) :throwOnError false)))}]
      default-loading-view)))

(defn render-mathjax [value]
  (let [mathjax (hooks/use-d3-require "https://run.nextjournalusercontent.com/data/QmQadTUYtF4JjbwhUFzQy9BQiK52ace3KqVHreUqL7ohoZ?filename=es5/tex-svg-full.js&content-type=application/javascript")
        ref-fn (react/useCallback (fn [el]
                                    (when el
                                      (let [r (.tex2svg js/MathJax value)]
                                        (if-let [c (.-firstChild el)]
                                          (.replaceChild el r c)
                                          (.appendChild el r)))))
                                  #js[value mathjax])]
    (if mathjax
      [:div.overflow-x-auto
       [:div {:ref ref-fn}]]
      default-loading-view)))

(def render-code code/render-code)

(def expand-icon
  [:svg {:xmlns "http://www.w3.org/2000/svg" :viewBox "0 0 20 20" :fill "currentColor" :width 12 :height 12}
   [:path {:fill-rule "evenodd" :d "M5.293 7.293a1 1 0 011.414 0L10 10.586l3.293-3.293a1 1 0 111.414 1.414l-4 4a1 1 0 01-1.414 0l-4-4a1 1 0 010-1.414z" :clip-rule "evenodd"}]])

(defn render-code-block [code-string {:as opts :keys [id]}]
  [:div.viewer.code-viewer.w-full.max-w-wide {:data-block-id id}
   [code/render-code code-string (assoc opts :language "clojure")]])

(defn render-folded-code-block [code-string {:as opts :keys [id]}]
  (let [!hidden? (hooks/use-state true)]
    (if @!hidden?
      [:div.relative.font-sans.text-slate-400.cursor-pointer.flex.overflow-y-hidden.group
       [:span.hover:text-slate-500
        {:class "text-[10px]"
         :on-click #(swap! !hidden? not)}
        "show code"]
       #_#_#_[:span.ml-4.opacity-0.translate-y-full.group-hover:opacity-100.group-hover:translate-y-0.transition-all.hover:text-slate-500
              {:class "text-[10px]"}
              "hide result"]
       [:span.ml-4.opacity-0.translate-y-full.group-hover:opacity-100.group-hover:translate-y-0.transition-all.delay-75.hover:text-slate-500
        {:class "text-[10px]"}
        "cached in memory"]
       [:span.ml-4.opacity-0.translate-y-full.group-hover:opacity-100.group-hover:translate-y-0.transition-all.delay-150.hover:text-slate-500
        {:class "text-[10px]"}
        "evaluated in 0.2s"]]
      [:<>
       [:div.relative.pl-12.font-sans.text-slate-400.cursor-pointer.flex.overflow-y-hidden.group.mb-1
        [:span.hover:text-slate-500
         {:class "text-[10px]"
          :on-click #(swap! !hidden? not)}
         "hide code"]
        #_#_#_[:span.ml-4.opacity-0.translate-y-full.group-hover:opacity-100.group-hover:translate-y-0.transition-all.hover:text-slate-500
               {:class "text-[10px]"}
               "hide result"]
        [:span.ml-4.opacity-0.translate-y-full.group-hover:opacity-100.group-hover:translate-y-0.transition-all.delay-75.hover:text-slate-500
         {:class "text-[10px]"}
         "cached in memory"]
        [:span.ml-4.opacity-0.translate-y-full.group-hover:opacity-100.group-hover:translate-y-0.transition-all.delay-150.hover:text-slate-500
         {:class "text-[10px]"}
         "evaluated in 0.2s"]]
       [:div.code-viewer.mb-2.relative.code-viewer.w-full.max-w-wide {:data-block-id id :style {:margin-top 0}}
        [render-code code-string (assoc opts :language "clojure")]]])))


(defn url-for [{:as src :keys [blob-id]}]
  (if (string? src)
    src
    (str "/_blob/" blob-id (when-let [opts (seq (dissoc src :blob-id))]
                             (str "?" (opts->query opts))))))

(def consume-view-context view-context/consume)
