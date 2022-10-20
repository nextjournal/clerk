(ns nextjournal.markdown
  "Babashka runtime stubs"
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [nextjournal.markdown.parser :as md.parser]))

(defn assert-quickjs! [] (assert (= 0 (:exit @(p/process '[which qjs]))) "QuickJS needs to be installed (brew install quickjs)"))
(def !md-mod-temp-dir (atom nil))
(defn md-mod-temp-dir []
  (or @!md-mod-temp-dir
      (let [tempdir (fs/create-temp-dir)]
        (assert-quickjs!)
        (spit (fs/file tempdir "markdown.mjs") (slurp (io/resource "js/markdown.mjs")))
        (reset! !md-mod-temp-dir (str tempdir)))))

(defn escape [t] (-> t (str/replace "`" "\\`") (str/replace "'" "\\'")))
(defn tokenize [text]
  (some-> (p/shell {:out :string :err :string :dir (md-mod-temp-dir)}
                   (str "qjs -e 'import(\"./markdown.mjs\").then((mod) => {print(mod.default.tokenizeJSON(`" (escape text) "`))})"
                        ".catch((e) => {import(\"std\").then((std) => { std.err.puts(\"cant find markdown module\"); std.exit(1)})})'"))
          deref :out not-empty
          (json/read-str {:key-fn keyword})))

(defn parse [md] {:type :doc :content []} (some-> md tokenize md.parser/parse))

(comment
  (assert-quickjs!)
  (md-mod-temp-dir)
  (tokenize "# Hello")
  (parse "# Hello
* `this`
* _is_
* crazy as [hello](https://hell.is)

$what$

---
```
and this is code
```
")
  (try (parse (slurp "notebooks/markdown.md"))
       (catch Exception e (:err (ex-data e)))))
