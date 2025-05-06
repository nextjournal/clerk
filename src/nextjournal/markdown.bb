(ns nextjournal.markdown
  "Babashka runtime stubs"
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [cheshire.core :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [nextjournal.markdown.transform :as md.transform]
            [nextjournal.markdown.utils :as utils]
            ))

(defn assert-quickjs! [] (assert (= 0 (:exit @(p/process '[which qjs]))) "QuickJS needs to be installed (brew install quickjs)"))
(def !md-mod-temp-dir (atom nil))
(defn md-mod-temp-dir []
  (or @!md-mod-temp-dir
      (let [tempdir (fs/create-temp-dir)]
        (assert-quickjs!)
        (spit (fs/file tempdir "markdown.mjs") (slurp (io/resource "js/markdown.mjs")))
        (reset! !md-mod-temp-dir (str tempdir)))))

(defn escape [t] (-> t (str/replace "\\" "\\\\\\") (str/replace "`" "\\`") (str/replace "'" "\\'")))
(defn tokenize [text]
  (some-> (p/shell {:out :string :err :string :dir (md-mod-temp-dir)}
                   (str "qjs -e 'import(\"./markdown.mjs\").then((mod) => {print(mod.default.tokenizeJSON(`" (escape text) "`))})"
                        ".catch((e) => {import(\"std\").then((std) => { std.err.puts(\"cant find markdown module\"); std.exit(1)})})'"))
          deref :out not-empty
          (json/parse-string true)))

(defn parse* [& args]
  ::TODO)

(defn parse
  [md] (some->> md tokenize
                ;; TODO
                ((requiring-resolve 'nextjournal.markdown.impl/parse))))

;; (defn re-groups* [m] (let [g (re-groups m)] (cond-> g (not (vector? g)) vector)))

;; (defn re-idx-seq
;;   "Takes a regex and a string, returns a seq of triplets comprised of match groups followed by indices delimiting each match."
;;   [re text]
;;   (let [m (re-matcher re text)]
;;     (take-while some? (repeatedly #(when (.find m) [(re-groups* m) (.start m) (.end m)])))))

;; (defn split-by-emoji [s]
;;   (let [[match start end] (first (re-idx-seq emoji/regex s))]
;;     (if match
;;       [(subs s start end) (str/trim (subs s end))]
;;       [nil s])))

;; (defn text->id+emoji [text]
;;   (when (string? text)
;;     (let [[emoji text'] (split-by-emoji (str/trim text))]
;;       (cond-> {:id (apply str (map (comp str/lower-case (fn [c] (case c (\space \_) \- c))) text'))}
;;         emoji (assoc :emoji emoji)))))

(def empty-doc
  {:type :doc
   :content []
   :toc {:type :toc}
   :footnotes []
   :text-tokenizers []
   ;; Node -> {id : String, emoji String}, dissoc from context to opt-out of ids
   :text->id+emoji-fn (comp utils/text->id+emoji md.transform/->text)

   ;; private
   ;; Id -> Nat, to disambiguate ids for nodes with the same textual content
   :nextjournal.markdown.impl/id->index {}
   ;; allow to swap between :doc or :footnotes
   :nextjournal.markdown.impl/root :doc})

(comment
  (assert-quickjs!)
  (md-mod-temp-dir)
  (tokenize "# Hello")
  (parse "# Hello
* `this`
* _is_ Some $\\mathfrak{M}$ formula
* crazy as [hello](https://hell.is)

---
```clojure
and this is code
```
")
  (try (parse (slurp "notebooks/markdown.md"))
       (catch Exception e (:err (ex-data e)))))
