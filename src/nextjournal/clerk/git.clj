(ns nextjournal.clerk.git
  "Clerk's Git integration for backlinks to source code repos."
  (:require [babashka.process :as p]
            [clojure.string :as str]))

(defn ^:private shell-out-str
  "Shell helper, calls a cmd and returns it output string trimmed."
  [cmd]
  (str/trim (:out (p/shell {:out :string} cmd))))

#_(shell-out-str "git rev-parse HEAD")
#_(shell-out-str "zonk")

(defn ->github-project [remote-url]
  (second (re-find #"^git@github\.com:(.*)\.git$" remote-url)))

(defn ->https-git-url
  "Takes a git `remote-url` and tries to convert it into a https url for
  backlinks. Currently only works for github, should be extended for
  gitlab, etc."
  [remote-url]
  (cond
    (str/starts-with? remote-url "https://")
    (str/replace remote-url #"\.git$" "")

    (->github-project remote-url)
    (str "https://github.com/%s" (->github-project remote-url))))

#_(->https-git-url "https://github.com/nextjournal/clerk.git")
#_(->https-git-url "git@github.com:nextjournal/clerk.git")

(defn read-git-attrs []
  (try {:git/sha (shell-out-str "git rev-parse HEAD")
        :git/url (some ->https-git-url
                       (map #(shell-out-str (str "git remote get-url " %))
                            (str/split-lines (shell-out-str "git remote"))))}
       (catch Exception _
         {})))

#_(read-git-attrs)
