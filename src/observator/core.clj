;; # Hello observator!!!! 👋
(ns observator.core
  (:refer-clojure :exclude [hash])
  (:require [clojure.java.classpath :as cp]
            [clojure.java.io :as io]
            [clojure.reflect :as reflect]
            [clojure.string :as str]
            [clojure.set :as set]
            [clojure.tools.analyzer.jvm :as ana]
            [clojure.tools.analyzer.passes.jvm.emit-form :as ana.passes.ef]
            [observator.lib :as obs.lib]
            [nextjournal.directory-watcher :as dw]
            [rewrite-clj.parser :as p]
            [rewrite-clj.node :as n]
            [datoteka.core :as fs])
  (:import (java.io FileInputStream LineNumberReader InputStreamReader PushbackReader)
           (clojure.lang RT)))

;; TODO
;; * fix issue when var is renamed: hash stays the same, dependents don't see new name
;; * identify symbols that point to libraries, first for Clojure, e.g. `{rewrite-clj.parser rewrite-clj/rewrite-clj}`
;; * research mapping of requires to library coords (no mapping, look into jar)
;; * use `clojure.reflect/reflect` to figure out

;; plot things with aeroic
;; recurse with try block but return an identifiable error

(defn fix-case [s]
  (obs.lib/fix-case s))

;; **Dogfooding** the system while constructing it, I'll try to make a
;; little bit of literate commentary. This is *literate* programming.
(def slow-thing
  (do
    (Thread/sleep 500)
    (map fix-case (str/split-lines (slurp "/usr/share/dict/words")))))

(count slow-thing)

(do ;; slow as well
  (Thread/sleep 500)
  42)

(def ^:observator/no-cache random-thing
  (rand-int 1000))

(def random-cached-thing
  (rand-int 1000))

(def md->html
  "Convert markdown to HTML."
  (let [md-parser (.build (org.commonmark.parser.Parser/builder))
        html-renderer (.build (org.commonmark.renderer.html.HtmlRenderer/builder))]
    (fn [md]
      (.render html-renderer (.parse md-parser md)))))

;; The following two functions determine the typographic conventions
;; for the viewer.

(defn make-syntax-pane
  "Create a new syntax-highlighting enabled text area set up for Clojure code."
  ([code]
   (make-syntax-pane code nil))
  ([code {:keys [background?]}]
   (doto (javax.swing.JScrollPane.
          (let [textarea (org.fife.ui.rsyntaxtextarea.RSyntaxTextArea. code (inc (count (clojure.string/split-lines code))) 80)]
            (when background?
              (.setBackground textarea (java.awt.Color. 245 245 245)))
            (doto textarea
              (.setPreferredSize (java.awt.Dimension. (count (clojure.string/split-lines code)) 80))
              (.setFont (java.awt.Font. "Fira Code" java.awt.Font/PLAIN 16))
              (.setSyntaxEditingStyle org.fife.ui.rsyntaxtextarea.SyntaxConstants/SYNTAX_STYLE_CLOJURE)
              (.setHighlightCurrentLine false)
              (.setBorder (javax.swing.BorderFactory/createEmptyBorder 12 12 12 12))
              ;;(.setBorder (javax.swing.border.LineBorder. java.awt.Color/black))
              (.setEditable false))))
     (.setBorder (javax.swing.BorderFactory/createEmptyBorder))
     (.setVerticalScrollBarPolicy javax.swing.ScrollPaneConstants/VERTICAL_SCROLLBAR_NEVER))))

(defn make-html-pane
  "Create a new text area that understands basic HTML formatting and looks not completely terrible."
  [html]
  (doto (javax.swing.JScrollPane.
         (doto (javax.swing.JTextPane.)
           (.setPreferredSize (java.awt.Dimension. (inc (count (clojure.string/split-lines html))) 80))
           (.putClientProperty javax.swing.JEditorPane/HONOR_DISPLAY_PROPERTIES true)
           (.setFont (java.awt.Font. "Georgia" java.awt.Font/PLAIN 20))
           (.setContentType "text/html")
           (.setText html)
           ;; (.setBorder (javax.swing.border.LineBorder. java.awt.Color/black))
           (.setEditable false)))
    (.setBorder (javax.swing.BorderFactory/createEmptyBorder))))

;; These next two definitions should not be global, but it is
;; convenient for them to be so during development. Ultimately, we
;; probably want multiple frame support to watch multiple files.

(defonce panel
  (let [p (javax.swing.JPanel.)]
    (doto p
      (.setBackground java.awt.Color/WHITE)
      (.setBorder (javax.swing.BorderFactory/createEmptyBorder 12 12 12 12))
      (.setLayout (javax.swing.BoxLayout. p javax.swing.BoxLayout/Y_AXIS)))))

(defonce frame
  (let [frame (javax.swing.JFrame.)]
    (.add (.getContentPane frame)
          (javax.swing.JScrollPane. panel))
    (doto frame
      (.pack)
      (.setSize 800 1200)
      (.setVisible true))))

(defn remove-leading-semicolons [s]
  (clojure.string/replace s #"^[;]+" ""))


(defn sha1-base64 [s]
  (String. (.encode (java.util.Base64/getUrlEncoder)
                    (.digest (java.security.MessageDigest/getInstance "SHA-1") (.getBytes s)))))

(comment
  (sha1-base64 "hello"))

(defn var-name
  "Takes a `form` and returns the name of the var, if it exists."
  [form]
  (when (and (sequential? form)
             (contains? '#{def defn} (first form)))
    (second form)))

(comment
  (var-name '(def hello :world)))

(defonce !var->hash
  (atom {}))

(comment
  {#'observator.demo/fix-case "duyPOA0UFJm5y9k3ZyDtC8veRak="}

  @!var->hash)

(defn declaring-classfiles [sym]
  (->> sym
       reflect/reflect
       :members
       (map :declaring-class)
       (map #(str (str/replace % "." fs/*sep*) ".class"))
       set))

(comment
  (declaring-classfiles clojure.core/add-tap)
  (declaring-classfiles io.methvin.watcher.DirectoryChangeEvent$EventType/CREATE)
  (declaring-classfiles nextjournal.directory-watcher/create))


(defn filenames-in-jar [path]
  (-> path
      io/file
      java.util.jar.JarFile.
      cp/filenames-in-jar
      set))

(comment
  (filenames-in-jar (nth (cp/classpath) 10))
  (filenames-in-jar (nth (cp/classpath) 16)))

(defn find-source
  "Searches the classpath for the source of a given var for a symbol `sym`.
  Returns either a path to the jar file or file."
  [sym]
  (when-let [resolved (resolve sym)]
    (some (fn [path]
            (if (cp/jar-file? path)
              (when (seq (set/intersection (declaring-classfiles sym) (filenames-in-jar path)))
                path)
              (some (fn [ext]
                      (let [file (str path fs/*sep* (str/replace (namespace sym) "." fs/*sep*) ext)]
                        (prn :ext ext :file file)
                        (when (fs/exists? file)
                          file))) [".cljc" ".clj"])))
          (cp/classpath))))

(comment
  (-> (cp/classpath)
      (nth 10)
      io/file
      java.util.jar.JarFile.
      cp/filenames-in-jar
      set)

  (resolve 'io.methvin.watcher.DirectoryChangeEvent$EventType)
  ;; clojure var precompiled in jar

  (-> #'nextjournal.directory-watcher/create meta)
  (-> #'observator.lib/fix-case meta)
  (-> #'observator.core/fix-case meta)
  (-> #'clojure.core/add-tap meta)

  (resolve 'io.methvin.watcher.DirectoryChangeEvent$EventType)
  (resolve 'io.methvin.watcher.DirectoryChangeEvent$EventType/CREATE)

  (reflect/reflect nextjournal.directory-watcher/create)
  (reflect/reflect clojure.core/add-tap)
  (find-source (type clojure.core/add-tap))
  ;; java class
  (find-source io.methvin.watcher.DirectoryChangeEvent$EventType/CREATE)
  ;; clojure var with source in jar TODO
  (find-source nextjournal.directory-watcher/create)
  )

(defn analyze+qualify [form]
  (-> form
      ana/analyze
      (ana.passes.ef/emit-form #{:qualified-symbols})))

(comment
  (analyze+qualify 'fix-case))


(defn required-namespaces
  "Takes a `form` and returns a set of the namespaces it requires."
  [form]
  (->> form
       (tree-seq sequential? seq)
       (keep (fn [form]
               (when (and (sequential? form)
                          (= (first form) 'clojure.core/require))
                 (into #{}
                       (map #(let [f (second %)]
                               (cond-> f (sequential? f) first)))
                       (rest form)))))
       (apply set/union)))


(comment
  (meta (find-ns 'observator.demo))

  (required-namespaces '(ns observator.core
                          (:require [observator.lib :as obs.lib]
                                    observator.demo)))

  (required-namespaces '(do (require '[observator.lib :as obs.lib]
                                     'observator.demo2)
                            (require 'observator.demo)))
  ;; TODO
  (required-namespaces '(require '(clojure zip [set :as s]))))

(declare read+eval-cached)

(defn eval+cache [code]
  (loop [nodes (:children (p/parse-string-all code))]
    (if-let [node (first nodes)]
      (recur (cond
               (= :list (n/tag node)) (do (read+eval-cached (n/string node))
                                          (rest nodes))
               :else (rest nodes))))))

(defn file-resource [var]
  (some-> var
          meta
          :file
          io/resource))

(comment
  ;; maybe analyze and hash whole file later?
  (let [var #'observator.lib/fix-case
        {:keys [file line]} (meta var)
        lines (-> file io/resource io/reader line-seq)]
    (->> lines
         (drop (dec line))
         (take 2))))


(defn var-dependencies [form]
  (let [form (analyze+qualify form)
        var-name (var-name form)]
    (->> form
         (tree-seq sequential? seq)
         (keep #(when (and (symbol? %)
                           (not= var-name %))
                  (resolve %)))
         (into #{}))))

#_(var-dependencies '(defn foo
                       ([] (foo "s"))
                       ([s] (str/includes? (obs.lib/fix-case s) "hi"))))

(declare hash)

(defn source-fn
  "Same as clojure.repl/source-fn but can handle absolute paths."
  [x]
  (when-let [v (resolve x)]
    (when-let [filepath (:file (meta v))]
      (when-let [strm (or (.getResourceAsStream (RT/baseLoader) filepath)
                          (FileInputStream. (io/file filepath)))]
        strm
        (with-open [rdr (LineNumberReader. (InputStreamReader. strm))]
          (dotimes [_ (dec (:line (meta v)))] (.readLine rdr))
          (let [text (StringBuilder.)
                pbr (proxy [PushbackReader] [rdr]
                      (read [] (let [i (proxy-super read)]
                                 (.append text (char i))
                                 i)))
                read-opts (if (.endsWith ^String filepath "cljc") {:read-cond :allow} {})]
            (if (= :unknown *read-eval*)
              (throw (IllegalStateException. "Unable to read source while *read-eval* is :unknown."))
              (read read-opts (PushbackReader. pbr)))
            (str text)))))))

(defn hash-var [var->hash visited var]
  (when-not (or (str/starts-with? (-> var meta :ns str) "clojure.")
                (visited var))
    (when-let [code-string (and (var? var)
                                (-> var
                                    symbol
                                    source-fn))]
      (binding [*ns* (-> var meta :ns)]
        (hash var->hash (conj visited var) (-> code-string read-string analyze+qualify))))))


(comment
  (hash-var {} #{} #'observator.core/fix-case))

(defn hash-dependencies
  "Takes a `form` and a mapping `var->hash` returns a sorted vector of the hashes of the vars
  it depends on."
  [var->hash visited form]
  (into {}
        (map (juxt identity (partial hash-var var->hash visited)))
        (var-dependencies form)))

#_(hash-dependencies {} #{} '(def foo obs.lib/fix-case))


(defn hash
  ([var->hash form]
   (hash var->hash #{} form))
  ([var->hash visited form]
   (sha1-base64 (pr-str (conj (-> (hash-dependencies var->hash visited form) vals set) form)))))

(comment
  (hash '(def foo (do slow-thing)) {(resolve 'slow-thing) "fd234c"}))


(defn read+eval-cached [code-string]
  (let [cache-dir (str fs/*cwd* fs/*sep* ".cache")
        form (-> code-string read-string analyze+qualify)
        hash (hash @!var->hash form)
        cache-file (str cache-dir fs/*sep* hash)]
    (fs/create-dir cache-dir)
    (if (fs/exists? cache-file)
      (read-string (slurp cache-file))
      (let [result (eval form)
            var-value (cond-> result (var? result) deref)]
        (when (var? result)
          (swap! !var->hash assoc result hash))
        (if (fn? var-value)
          result
          (do (when-not (or (-> result meta :observator/no-cache)
                            (instance? clojure.lang.IDeref var-value))
                (spit cache-file (pr-str var-value)))
              var-value))))))

(comment
  (def slow-thing-1 (do (Thread/sleep 500) 42))
  (inc slow-thing-1))


(defn clear-cache!
  ([]
   (reset! !var->hash {})
   (let [cache-dir (str fs/*cwd* fs/*sep* ".cache")]
     (when (fs/exists? cache-dir)
       (fs/delete (str fs/*cwd* fs/*sep* ".cache")))))
  ([sym]
   (let [var (resolve sym)]
     (when-let [cache-file (get @!var->hash var)]
       (when (fs/exists? cache-file)
         (fs/delete cache-file)))
     (swap! !var->hash dissoc var))))


(range 1000)

(comment
  (let [r (defn foo [] :bar)
        v (cond-> r (var? r) deref)]
    (fn? v)))

(defn format-eval-output [form]
  (binding [*print-length* 10]
    (pr-str form)))

(comment
  (format-eval-output (read+eval-cached "(+ 1 2 3)")))

(defn code->panel
  "Converts the Clojure source test in `code` to a series of text or syntax panes and causes `panel` to contain them."
  [panel code]
  (.removeAll panel)
  (loop [nodes (:children (p/parse-string-all code))]
    (if-let [node (first nodes)]
      (recur (cond
               (= :list (n/tag node)) (do (.add panel
                                                (make-syntax-pane (n/string node) {:background? true}))
                                          (.add panel
                                                (make-syntax-pane (format-eval-output (read+eval-cached (n/string node)))))
                                          (rest nodes))
               (n/comment? node) (do (.add panel (make-html-pane
                                                  (md->html
                                                   (apply str (map (comp remove-leading-semicolons n/string)
                                                                   (take-while n/comment? nodes))))))
                                     (drop-while n/comment? nodes))
               :else (rest nodes)))))
  (.add panel (javax.swing.JTextPane.))
  (.validate (.getContentPane frame))
  (.repaint frame))

(defn file-event [{:keys [type path]}]
  (when-let [ns-part (and (= type :modify)
                          (second (re-find #".*/src/(.*)\.clj" (str path))))]
    (binding [*ns* (find-ns (symbol (str/replace ns-part fs/*sep* ".")))]
      (code->panel panel (slurp path)))))

;; And, as is the culture of our people, a commend block containing
;; pieces of code with which to pilot the system during development.

(comment
  (def watcher
    (doto (dw/create #(file-event %) "src")
      dw/watch))

  (dw/stop watcher)
  (time (code->panel panel (slurp "src/observator/core.clj")))

  ;; Clear cache
  (clear-cache!)
  (clear-cache! 'random-cached-thing)
  )
