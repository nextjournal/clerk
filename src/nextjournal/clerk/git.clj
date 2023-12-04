(ns nextjournal.clerk.git
  "Clerk's Git integration for backlinks to source code repos."
  (:require [babashka.process :as p]
            [clojure.string :as str]))

(defn ^:private  shell-out-str
  "Shell helper, calls a cmd and returns it output string trimmed."
  [& cmd]
  (str/trim (:out (p/check (apply p/sh {:out :string} cmd)))))

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
    (str "https://github.com/" (->github-project remote-url))))

#_(->https-git-url "https://github.com/nextjournal/clerk.git")
#_(->https-git-url "git@github.com:nextjournal/clerk.git")

(defn read-git-attrs []
  (try {:git/sha (shell-out-str "git rev-parse HEAD")
        :git/url (let [branch (shell-out-str "git symbolic-ref --short HEAD")
                       remote (shell-out-str "git" "config" (str "branch." branch ".remote"))
                       remote-url (shell-out-str "git" "remote" "get-url" remote)]
                   (->https-git-url remote-url))}
       (catch Exception e
         #_(prn e)
         {})))

#_(read-git-attrs)
