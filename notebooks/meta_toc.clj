;; # 📕 Meta Table of Contents
(ns meta-toc
  {:nextjournal.clerk/toc true
   :nextjournal.clerk/no-cache true}
  (:require [babashka.fs :as fs]
            [clojure.edn]
            [clojure.string :as str]
            [nextjournal.clerk :as clerk]
            [nextjournal.clerk.parser :as parser]
            [nextjournal.clerk.viewer :as viewer]
            [nextjournal.markdown.transform :as md.transform]))

;; ## Notebooks
(def paths
  ["notebooks/rule_30.clj"
   "notebooks/visibility.clj"
   "notebooks/viewer_api.clj"
   "notebooks/viewer_classes.clj"])

;; TODO: something closer to a ns
(defn path->title [path]
  (-> path fs/file-name (str/replace #"\.(clj(c?)|md)$" "") (str/split #"_")
      (->> (map str/capitalize)
           (str/join " "))))
#_(path->title "src/ductile/clerk/doc.clj")

(defn md-toc->navbar-item [path {:as item :keys [children emoji attrs]}]
  (cond-> {:title (cond-> (or (not-empty (md.transform/->text item)) (path->title path))
                          (seq emoji) (subs (count emoji)))
           :expanded? true
           :emoji emoji
           :path (str "#" (:id attrs))}
          (seq children)
          (assoc :items (mapv (comp #(assoc % :expandable-toc? false)
                                    (partial md-toc->navbar-item path)) children))))

(defn path->doc-info [path]
  (let [{:keys [title toc]} (parser/parse-file {:doc? true} path)]
    {:path path
     :toc toc
     ;; enforce only ATX-1 as title
     :title (or (not-empty (-> toc :children first md.transform/->text))
                (path->title path))}))

#_(:toc (parser/parse-file {:doc? true} "roadmap/compound.md"))
#_(->> (parser/parse-file {:doc? true} "src/ductile/clerk/doc.clj")
       :toc :children
       (mapv (partial md-toc->navbar-item "src/ductile/clerk/doc.clj")))

(defn doc-path->path-in-registry [registry folder-path]
  (let [index-of-matching (fn [r] (first (keep-indexed #(when (str/starts-with? folder-path (:path %2)) %1) (:items r))))]
    (loop [r registry
           nav-path [:items]]
      (if-some [idx (index-of-matching r)]
        (recur (get-in r (conj nav-path idx))
               (conj nav-path idx :items))
        nav-path))))

(defn normalize-atx1s [[first & rest]]
  (cond-> first
          (seq rest)
          (update :items
                  (fnil into [])
                  (mapv #(assoc % :expandable-toc? false) rest))))

(defn remove-ext [s] (str/replace s #"\.(clj|md)$" ""))
#_(remove-ext "src/doc/clerk.mdx")

(defn add-collection [{:keys [index current-notebook]} registry [parent-path coll]]
  (if (str/blank? parent-path)
    (assoc registry :items coll)
    (update-in registry
               (doc-path->path-in-registry registry parent-path)
               (fnil conj []) {:title (path->title parent-path)
                               :expanded? (or (= current-notebook index)
                                              (str/starts-with? (str current-notebook) parent-path))
                               :folder? true
                               :href "#"
                               :path parent-path
                               :items (mapv (fn [{:as item :keys [toc path]}]
                                              (-> item
                                                  (update :path (comp clerk/doc-url remove-ext))
                                                  (dissoc :toc)
                                                  (as-> item
                                                        (if (= current-notebook path)
                                                          (assoc (if-some [cs (seq (:children toc))]
                                                                   (normalize-atx1s (mapv (partial md-toc->navbar-item path) cs))
                                                                   item) :css-class "font-bold")
                                                          item)))) coll)})))

#_(def current-notebook "src/ductile/clerk/doc.clj")
#_(def add-collection* (partial add-collection current-notebook))
#_(-> {}
      (add-collection* ["roadmap" [{:title "Compound" :path "roadmap/compound.clj"}
                                   {:path "roadmap/billing.clj" :title "z"}
                                   {:title "EDI" :path "roadmap/edi.clj"}]])
      (add-collection* ["roadmap/ars" [{:title "xxx" :path "roadmap/ars/billing.clj"}
                                       {:title "xx" :path "roadmap/ars/compound.clj"}]]))

(defn intersect? [p1 p2] (or (str/starts-with? p1 p2) (str/starts-with? p2 p1)))

(defn compare-path-length-then-order [paths p1 p2]
  (if (not (intersect? (str (fs/parent p1)) (str (fs/parent p2))))
    (compare (.indexOf paths p1) (.indexOf paths p2))
    (let [d (compare (count (seq (fs/path p1)))
                     (count (seq (fs/path p2))))]
      (if (zero? d)
        (compare (.indexOf paths p1) (.indexOf paths p2))
        d))))

(defn meta-toc [{:as opts :keys [paths]}]
  (->> paths
       (map path->doc-info)
       (group-by (comp str fs/parent fs/path :path))
       (sort-by (comp :path first val) (partial compare-path-length-then-order paths))
       (reduce (partial add-collection opts) {})
       :items))

#_(meta-toc {:current-notebook "src/ductile/clerk/doc.clj"
             :paths ductile.clerk/leaflet-paths})

(defn next-path [paths x dir] (when-some [i (.indexOf paths x)] (get (vec paths) (+ i dir))))
(defn update-header [[tag & contents] {:keys [current-notebook paths]}]
  (let [sorted-paths (sort (partial compare-path-length-then-order paths) paths)
        prev-path (next-path sorted-paths current-notebook -1)
        next-path (next-path sorted-paths current-notebook +1)]
    (vec
      (cons tag
            (cond-> ()
                    prev-path
                    (into [[:span.mx-2 "•"]
                           [:a.font-medium.border-b.border-dotted.border-slate-300.hover:text-indigo-500.hover:border-indigo-500.dark:border-slate-500.dark:hover:text-white.dark:hover:border-white.transition
                            {:href (viewer/doc-url (remove-ext prev-path))} "Prev"]])
                    true
                    (concat contents)
                    next-path
                    (concat [[:span.mx-2 "•"]
                             [:a.font-medium.border-b.border-dotted.border-slate-300.hover:text-indigo-500.hover:border-indigo-500.dark:border-slate-500.dark:hover:text-white.dark:hover:border-white.transition
                              {:href (viewer/doc-url (remove-ext next-path))} "Next"]]))))))

(defn notebook-viewer [opts]
  (update viewer/notebook-viewer
          :transform-fn (fn [original-transform]
                          (fn [wrapped-value]
                            (let [opts (assoc opts :current-notebook (:file (viewer/->value wrapped-value)))]
                              (-> wrapped-value
                                  original-transform
                                  (assoc :nextjournal/render-opts {:expandable-toc? true})
                                  (assoc-in [:nextjournal/value :toc-visibility] true)
                                  (assoc-in [:nextjournal/value :toc] (meta-toc opts))
                                  (update-in [:nextjournal/value :header :nextjournal/value 1] update-header opts)))))))

(comment
  ;; Test actual cross-doc toc
  (viewer/reset-viewers! :default (viewer/add-viewers [(notebook-viewer {:paths paths})]))
  (reset! viewer/!viewers {})
  (clerk/build! {:xhr? true :paths paths}))
