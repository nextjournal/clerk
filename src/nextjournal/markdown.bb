(ns nextjournal.markdown
  "Babashka runtime stubs"
  (:require [babashka.process :as p]
            [clojure.data.json :as json]
            [nextjournal.markdown.parser :as md.parser]
            [clojure.string :as str]))

(defn escape [t] (-> t (str/replace "`" "\\`") (str/replace "'" "\\'")))
(defn tokenize [text]
  (some-> (babashka.process/shell {:out :string :err :string}
                                  (str "qjs -e 'import(\"./js/markdown.mjs\").then((mod) => {print(mod.default.tokenizeJSON(`" (escape text) "`))})"
                                       ".catch((e) => {import(\"std\").then((std) => { std.err.puts(\"cant find markdown module\"); std.exit(1)})})'"))
      :out not-empty
      (json/read-str {:key-fn keyword})))

(defn parse [md] (some-> md tokenize md.parser/parse))

(comment
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
