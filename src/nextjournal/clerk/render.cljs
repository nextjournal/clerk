(ns nextjournal.clerk.render
  (:require ["framer-motion" :refer [motion]]
            ["react" :as react]
            ["react-dom/client" :as react-client]
            ["vh-sticky-table-header" :as sticky-table-header]
            [applied-science.js-interop :as j]
            [cljs.reader]
            [clojure.set :as set]
            [clojure.string :as str]
            [clojure.walk :as w]
            [editscript.core :as editscript]
            [goog.events :as gevents]
            [goog.object]
            [goog.string :as gstring]
            [nextjournal.clerk.render.code :as code]
            [nextjournal.clerk.render.context :as view-context]
            [nextjournal.clerk.render.hooks :as hooks]
            [nextjournal.clerk.render.localstorage :as localstorage]
            [nextjournal.clerk.render.navbar :as navbar]
            [nextjournal.clerk.render.window :as window]
            [nextjournal.clerk.viewer :as viewer]
            [nextjournal.markdown.transform :as md.transform]
            [reagent.core :as r]
            [reagent.ratom :as ratom]
            [sci.core :as sci]
            [sci.ctx-store]
            [shadow.cljs.modern :refer [defclass]]))


(r/set-default-compiler! (r/create-compiler {:function-components true}))

(declare inspect inspect-presented reagent-viewer html html-viewer)

(def nbsp (gstring/unescapeEntities "&nbsp;"))

(defn reagent-atom? [x]
  (satisfies? ratom/IReactiveAtom x))

(defn toc-items [items]
  (reduce
   (fn [acc {:as item :keys [content children attrs emoji]}]
     (if content
       (let [title (md.transform/->text item)]
         (->> {:title title
               :emoji emoji
               :path (str "#" (:id attrs))
               :items (toc-items children)}
              (conj acc)
              vec))
       (toc-items (:children item))))
   []
   items))

(defn dark-mode-toggle [!state]
  (let [{:keys [dark-mode?]} @!state
        spring {:type :spring :stiffness 200 :damping 10}]
    [:div.relative.dark-mode-toggle
     [:button.text-slate-400.hover:text-slate-600.dark:hover:text-white.cursor-pointer
      {:on-click #(swap! !state assoc :dark-mode? (not dark-mode?))}
      (if dark-mode?
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
         [:>(.-circle motion)
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

(def local-storage-dark-mode-key "clerk-darkmode")

(defn set-dark-mode! [dark-mode?]
  (let [class-list (.-classList (js/document.querySelector "html"))]
    (if dark-mode?
      (.add class-list "dark")
      (.remove class-list "dark")))
  (localstorage/set-item! local-storage-dark-mode-key dark-mode?))

(defn setup-dark-mode! [!state]
  (let [{:keys [dark-mode?]} @!state]
    (add-watch !state ::dark-mode
               (fn [_ _ old {:keys [dark-mode?]}]
                 (when (not= (:dark-mode? old) dark-mode?)
                   (set-dark-mode! dark-mode?))))
    (when dark-mode?
      (set-dark-mode! dark-mode?))))

(defonce !eval-counter (r/atom 0))

(defn exec-status [{:keys [progress status]}]
  [:div.w-full.bg-purple-200.dark:bg-purple-900.rounded.z-20 {:class "h-0.5"}
   [:div.bg-purple-600.dark:bg-purple-400 {:class "h-0.5" :style {:width (str (* progress 100) "%")}}]
   [:div.absolute.text-purple-600.dark:text-white.text-xs.font-sans.ml-1.bg-white.dark:bg-purple-900.rounded-full.shadow.z-20.font-bold.px-2.border.border-slate-300.dark:border-purple-400
    {:style {:font-size "0.5rem"} :class "left-[35px] md:left-0 mt-[7px] md:mt-1"}
    status]])

(defn connection-status [status]
  [:div.absolute.text-red-600.dark:text-white.text-xs.font-sans.ml-1.bg-white.dark:bg-red-800.rounded-full.shadow.z-20.font-bold.px-2.border.border-red-400
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

(defn ->URL [^js href]
  (js/URL. href))

(defn handle-anchor-click [^js e]
  (when-some [url (some-> e .-target closest-anchor-parent .-href ->URL)]
    (when (= (.-search url) "?clerk/show!")
      (.preventDefault e)
      (clerk-eval (list 'nextjournal.clerk.webserver/navigate!
                        (cond-> {:nav-path (subs (.-pathname url) 1)}
                          (seq (.-hash url))
                          (assoc :fragment (subs (.-hash url) 1))))))))

(defn history-push-state [{:as opts :keys [path fragment replace?]}]
  (when (not= path (some-> js/history .-state .-path))
    (j/call js/history (if replace? :replaceState :pushState) (clj->js opts) "" (str "/" path (when fragment (str "#" fragment))))  ))

(defn handle-history-popstate [^js e]
  (when-let [{:as opts :keys [path]} (js->clj (.-state e) :keywordize-keys true)]
    (.preventDefault e)
    (clerk-eval (list 'nextjournal.clerk.webserver/navigate! {:nav-path path :skip-history? true}))))

(defn render-notebook [{:as _doc xs :blocks :keys [bundle? doc-css-class sidenotes? toc toc-visibility header footer]} opts]
  (r/with-let [local-storage-key "clerk-navbar"
               navbar-width 220
               !state (r/atom {:toc (toc-items (:children toc))
                               :visibility toc-visibility
                               :md-toc toc
                               :dark-mode? (localstorage/get-item local-storage-dark-mode-key)
                               :theme {:slide-over "bg-slate-100 dark:bg-gray-800 font-sans border-r dark:border-slate-900"}
                               :width navbar-width
                               :mobile? (and (exists? js/innerWidth) (< js/innerWidth 640))
                               :mobile-width 300
                               :local-storage-key local-storage-key
                               :set-hash? (not bundle?)
                               :scroll-el (when (exists? js/document) (js/document.querySelector "html"))
                               :open? (if-some [stored-open? (localstorage/get-item local-storage-key)]
                                        stored-open?
                                        (not= :collapsed toc-visibility))})
               root-ref-fn (fn [el]
                             (when (and el (exists? js/document))
                               (setup-dark-mode! !state)
                               (when-some [heading (when (and (exists? js/location) (not bundle?))
                                                     (try (some-> js/location .-hash not-empty js/decodeURI (subs 1) js/document.getElementById)
                                                          (catch js/Error _
                                                            (js/console.warn (str "Clerk render-notebook, invalid hash: "
                                                                                  (.-hash js/location))))))]
                                 (js/requestAnimationFrame #(.scrollIntoViewIfNeeded heading)))))]
    (let [{:keys [md-toc mobile? open? visibility]} @!state
          doc-inset (cond
                      mobile? 0
                      open? navbar-width
                      :else 0)]
      (when-not (= md-toc toc)
        (swap! !state assoc :toc (toc-items (:children toc)) :md-toc toc :open? open?))
      (when-not (= visibility toc-visibility)
        (swap! !state assoc :visibility toc-visibility :open? (not= :collapsed toc-visibility)))
      [:div.flex
       {:ref root-ref-fn}
       [:div.fixed.top-2.left-2.md:left-auto.md:right-2.z-10
        [dark-mode-toggle !state]]
       (when (and toc toc-visibility)
         [:<>
          [navbar/toggle-button !state
           [:<>
            [:svg {:xmlns "http://www.w3.org/2000/svg" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor" :width 20 :height 20}
             [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M4 6h16M4 12h16M4 18h16"}]]
            [:span.uppercase.tracking-wider.ml-1.font-bold
             {:class "text-[12px]"} "ToC"]]
           {:class "z-10 fixed right-2 top-2 md:right-auto md:left-3 md:top-[7px] text-slate-400 font-sans text-xs hover:underline cursor-pointer flex items-center bg-white dark:bg-gray-900 py-1 px-3 md:p-0 rounded-full md:rounded-none border md:border-0 border-slate-200 dark:border-gray-500 shadow md:shadow-none dark:text-slate-400 dark:hover:text-white"}]
          [navbar/panel !state [navbar/navbar !state]]])
       [:div.flex-auto.w-screen.scroll-container
        (into
         [:> (.-div motion)
          {:key "notebook-viewer"
           :initial (when toc-visibility {:margin-left doc-inset})
           :animate (when toc-visibility {:margin-left doc-inset})
           :transition navbar/spring
           :class (cond-> (or doc-css-class [:flex :flex-col :items-center :notebook-viewer :flex-auto])
                    sidenotes? (conj :sidenotes-layout))}]
         ;; TODO: restore react keys via block-id
         ;; ^{:key (str processed-block-id "@" @!eval-counter)}

         (inspect-children opts) (concat (when header [header]) xs (when footer [footer])))]])))

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
       (when (get-in x [:nextjournal/opts :fragment-item?]) "fragment-item")
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
                 (get-in x [:nextjournal/opts :id])
                 (with-meta {:key (str (get-in x [:nextjournal/opts :id]) "@" @!eval-counter)})))))

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

(defn render-coll [xs {:as opts :keys [closing-parens path viewer !expanded-at] :or {path []}}]
  (let [expanded? (get @!expanded-at path)
        {:keys [opening-paren closing-paren]} viewer]
    [:span.inspected-value.whitespace-nowrap
     {:class (when expanded? "inline-flex")}
     [:span
      (if (expandable? xs)
        [expand-button !expanded-at opening-paren path]
        [:span opening-paren])
      (into [:<>]
            (comp (inspect-children opts)
                  (interpose (if expanded? [:<> [:br] triangle-spacer nbsp (when (= 2 (count opening-paren)) nbsp)] " ")))
            xs)
      (into [:span] (or closing-parens [closing-paren]))]]))

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


(defn render-string [s {:as opts :keys [path !expanded-at] :or {path []}}]
  (let [expanded? (get @!expanded-at path)]
    (into [:span.whitespace-pre]
          (map #(if (string? %)
                  (if expanded?
                    (into [:<>] (interpose [:<> [:br]] (str/split-lines %)))
                    (into [:<>] (interpose [:span.text-slate-400 "↩︎"] (str/split-lines %))))
                  (inspect-presented opts %)))
          (if (string? s) [s] s))))

(defn render-quoted-string [s {:as opts :keys [closing-parens path viewer !expanded-at] :or {path []}}]
  (let [{:keys [opening-paren closing-paren]} viewer]
    [:span.inspected-value.inline-flex
     [:span.cmt-string
      (if (some #(and (string? %) (str/includes? % "\n")) (if (string? s) [s] s))
        [expand-button !expanded-at opening-paren path]
        [:span opening-paren])]
     (into [:div
            [:span.cmt-string (viewer/->value (render-string s opts)) (first closing-paren)]
            (rest closing-parens)])]))

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

(def x-icon
  [:svg.h-4.w-4 {:xmlns "http://www.w3.org/2000/svg" :viewBox "0 0 20 20" :fill "currentColor"}
   [:path {:fill-rule "evenodd" :d "M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" :clip-rule "evenodd"}]])

(def check-icon
  [:svg.h-4.w-4 {:xmlns "http://www.w3.org/2000/svg" :viewBox "0 0 20 20" :fill "currentColor"}
   [:path {:fill-rule "evenodd" :d "M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" :clip-rule "evenodd"}]])

(defn render-table-error [[data]]
  ;; currently boxing the value in a vector to retain the type info
  ;; TODO: find a better way to do this
  [:div.bg-red-100.dark:bg-gray-800.px-6.py-4.rounded-md.text-xs.dark:border-2.dark:border-red-400.not-prose
   [:h4.mt-0.uppercase.text-xs.dark:text-red-400.tracking-wide "Table Error"]
   [:p.mt-4.font-medium "Clerk’s table viewer does not recognize the format of your data:"]
   [:div.mt-2.flex
    [:div.text-red-500.mr-2 x-icon]
    [inspect-presented data]]
   [:p.mt-4.font-medium "Currently, the following formats are supported:"]
   [:div.mt-2.flex.items-center
    [:div.text-green-500.mr-2 check-icon]
    [inspect {:column-1 [1 2]
              :column-2 [3 4]}]]
   [:div.mt-2.flex.items-center
    [:div.text-green-500.mr-2 check-icon]
    [inspect [{:column-1 1 :column-2 3} {:column-1 2 :column-2 4}]]]
   [:div.mt-2.flex.items-center
    [:div.text-green-500.mr-2 check-icon]
    [inspect [[1 3] [2 4]]]]
   [:div.mt-2.flex.items-center
    [:div.text-green-500.mr-2 check-icon]
    [inspect {:head [:column-1 :column-2]
              :rows [[1 3] [2 4]]}]]])

(defn render-table-with-sticky-header [& children]
  (let [!table-ref (hooks/use-ref nil)
        !table-clone-ref (hooks/use-ref nil)]
    (hooks/use-layout-effect (fn []
                               (when (and @!table-ref (.querySelector @!table-ref "thead") @!table-clone-ref)
                                 (let [sticky (sticky-table-header/StickyTableHeader. @!table-ref @!table-clone-ref #js{:max 0})]
                                   (fn [] (.destroy sticky))))))
    [:div
     [:div.overflow-x-auto.overflow-y-hidden.w-full
      (into [:table.text-xs.sans-serif.text-gray-900.dark:text-white.not-prose {:ref !table-ref}] children)]
     [:div.overflow-x-auto.overflow-y-hidden.w-full.shadow.sticky-table-header
      [:table.text-xs.sans-serif.text-gray-900.dark:text-white.not-prose {:ref !table-clone-ref :style {:margin 0}}]]]))

(defn throwable-view [{:keys [via trace]}]
  [:div.bg-white.max-w-6xl.mx-auto.text-xs.monospace.not-prose
   (into
    [:div]
    (map
     (fn [{:as _ex :keys [type message data _trace]}]
       [:div.p-4.bg-red-100.border-b.border-b-gray-300
        (when type
          [:div.font-bold "Unhandled " type])
        [:div.font-bold.mt-1 message]
        (when data
          [:div.mt-1 [inspect data]])])
     via))
   [:div.py-6.overflow-x-auto
    [:table.w-full
     (into [:tbody]
           (map (fn [[call _x file line]]
                  [:tr.hover:bg-red-100.leading-tight
                   [:td.text-right.px-6 file ":"]
                   [:td.text-right.pr-6 line]
                   [:td.py-1.pr-6 call]]))
           trace)]]])

(defn render-throwable [ex]
  (if (or (:stack ex) (instance? js/Error ex))
    [error-view ex]
    [throwable-view ex]))

(defn render-tagged-value
  ([tag value] (render-tagged-value {:space? true} tag value))
  ([{:keys [space?]} tag value]
   [:span.inspected-value.whitespace-nowrap
    [:span.cmt-meta tag] (when space? nbsp) value]))

(defonce !doc (ratom/atom nil))
(defonce !viewers viewer/!viewers)

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
     (let [{:nextjournal/keys [value viewer] :keys [path]} x]
       #_(prn :inspect-presented value :valid-element? (react/isValidElement value) :viewer viewer)
       ;; each view function must be called in its own 'functional component' so that it gets its own hook state.
       ^{:key (str (:hash viewer) "@" (peek (:path opts)))}
       [(:render-fn viewer) value (merge opts
                                         (:nextjournal/opts x)
                                         {:viewer viewer :path path})]))))

(defn inspect [value]
  (r/with-let [!state (r/atom nil)]
    (when (not= (:value @!state ::not-found) value)
      (swap! !state assoc
             :value value
             :desc (viewer/present value)))
    [view-context/provide {:fetch-fn (fn [fetch-opts]
                                       (.then (let [{:keys [present-elision-fn]} (-> !state deref :desc meta)]
                                                (.resolve js/Promise (present-elision-fn fetch-opts)))
                                              (fn [more]
                                                (swap! !state update :desc viewer/merge-presentations more fetch-opts))))}
     [inspect-presented (:desc @!state)]]))

(defn show-window [& content]
  [window/show content])

(defn root []
  [:<>
   [inspect-presented @!doc]
   [:div.fixed.w-full.z-20.top-0.left-0.w-full
    (when-let [status (:nextjournal.clerk.sci-env/connection-status @!doc)]
      [connection-status status])
    (when-let [status (:status @!doc)]
      [exec-status status])]
   (when-let [error (get-in @!doc [:nextjournal/value :error])]
     [:div.fixed.top-0.left-0.w-full.h-full
      [inspect-presented error]])])

(declare mount)

(defonce ^:private ^:dynamic *sync* true)

(defn ws-send! [msg]
  (if (exists? js/ws_send)
    (js/ws_send (pr-str msg))
    (js/console.warn "Clerk can't send websocket message in static build, skipping...")))

(defn atom-changed [var-name _atom _old-state new-state]
  (when *sync*
    ;; TODO: for now sending whole state but could also diff
    (ws-send! {:type :swap! :var-name var-name :args [(list 'fn ['_] (list 'quote new-state))]})))

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

(defn re-eval-viewer-fns [doc]
  (let [re-eval (fn [{:keys [form]}] (viewer/->viewer-fn form))]
    (w/postwalk (fn [x] (cond-> x (viewer/viewer-fn? x) re-eval)) doc)))

(defn replace-viewer-fns [doc]
  (w/postwalk-replace (:hash->viewer doc) (dissoc doc :hash->viewer)))

(defn ^:export set-state! [{:as state :keys [doc]}]
  (when (contains? state :doc)
    (when (exists? js/window)
      ;; TODO: can we restore the scroll position when navigating back?
      (.scrollTo js/window #js {:top 0}))
    (reset! !doc (replace-viewer-fns doc)))
  ;; (when (and error (contains? @!doc :status))
  ;;   (swap! !doc dissoc :status))
  (when (remount? doc)
    (swap! !eval-counter inc))
  (when-let [title (and (exists? js/document) (-> doc viewer/->value :title))]
    (set! (.-title js/document) title)))

(defn apply-patch [x patch]
  (editscript/patch x (editscript/edits->script patch)))

(defn patch-state! [{:keys [patch]}]
  (if (remount? patch)
    (do (swap! !doc #(re-eval-viewer-fns (apply-patch % patch)))
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
        (if error (reject error) (resolve reply)))
    (js/console.warn :process-eval-reply!/not-found :eval-id eval-id :keys (keys @!pending-clerk-eval-replies))))

(defn ^:export dispatch [{:as msg :keys [type]}]
  (let [dispatch-fn (get {:patch-state! patch-state!
                          :set-state! set-state!
                          :eval-reply process-eval-reply!}
                         type
                         (fn [_]
                           (js/console.warn (str "no on-message dispatch for type `" type "`"))))]
    #_(js/console.log :<= type := msg)
    (dispatch-fn msg)))

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

(defn handle-initial-load [_]
  (history-push-state {:path (subs js/location.pathname 1) :replace? true}))

(defn path-from-url-hash [url]
  (-> url ->URL .-hash (subs 2)))

(defn handle-hashchange [{:keys [url->path path->doc]} ^js e]
  (let [url (some-> e .-event_ .-newURL path-from-url-hash)]
    (when-some [doc (get path->doc (get url->path url))]
      (set-state! {:doc doc}))))

(defn listeners [{:as state :keys [mode]}]
  (case mode
    :path
    #{(gevents/listen js/document gevents/EventType.CLICK handle-anchor-click false)
      (gevents/listen js/window gevents/EventType.POPSTATE handle-history-popstate false)
      (gevents/listen js/window gevents/EventType.LOAD handle-initial-load false)}

    :fragment
    #{(gevents/listen js/window gevents/EventType.HASHCHANGE (partial handle-hashchange state) false)}))

(defn setup-router! [{:as state :keys [mode]}]
  (when (and (exists? js/document) (exists? js/window))
    (doseq [listener (:listeners @!router)]
      (gevents/unlistenByKey listener))
    (reset! !router (assoc state :listeners (listeners state)))))


(defn ^:export mount []
  (when (and react-root (not hydrate?))
    (.render react-root (r/as-element [root]))))

(defn ^:dev/after-load ^:after-load re-render []
  (swap! !doc re-eval-viewer-fns)
  (mount))

(defn ^:export init [{:as state :keys [bundle? path->doc path->url current-path]}]
  (let [static-app? (contains? state :path->doc)] ;; TODO: better check
    (if static-app?
      (let [url->path (set/map-invert path->url)]
        (when bundle? (setup-router! (assoc state :mode :fragment :url->path url->path)))
        (set-state! {:doc (get path->doc (or current-path
                                             (when (and bundle? (exists? js/document))
                                               (url->path (path-from-url-hash (.-location js/document))))
                                             (url->path "")))})
        (mount))
      (do
        (setup-router! {:mode :path})
        (set-state! state)
        (mount)))))


(defn html-render [markup]
  (r/as-element
   (if (string? markup)
     [:span {:dangerouslySetInnerHTML {:__html markup}}]
     markup)))

(def html-viewer
  {:render-fn html-render})

(def html
  (partial viewer/with-viewer html-viewer))

(defn render-reagent [x]
  (r/as-element (cond-> x (fn? x) vector)))

;; TODO: remove
(def reagent-viewer render-reagent)

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
      [:span {:dangerouslySetInnerHTML {:__html (.renderToString katex tex-string (j/obj :displayMode (not inline?)))}}]
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

(defn render-code-block [code-string {:keys [id]}]
  [:div.viewer.code-viewer.w-full.max-w-wide {:data-block-id id}
   [code/render-code code-string]])

(defn render-folded-code-block [code-string {:keys [id]}]
  (let [!hidden? (hooks/use-state true)]
    (if @!hidden?
      [:div.relative.pl-12.font-sans.text-slate-400.cursor-pointer.flex.overflow-y-hidden.group
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
        [render-code code-string]]])))


(defn url-for [{:as src :keys [blob-id]}]
  (if (string? src)
    src
    (str "/_blob/" blob-id (when-let [opts (seq (dissoc src :blob-id))]
                             (str "?" (opts->query opts))))))

(def consume-view-context view-context/consume)
