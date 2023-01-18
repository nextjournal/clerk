;; # Sidenotes
(ns sidenotes
  (:require [nextjournal.clerk.parser :as parser]))

;; here we should write something [^note] here we should write something here we should write something here we should write something
;; here we should write somethinghere we should write somethinghere we should write somethinghere we should write something
;; here we should write somethinghere we should write somethinghere we should write somethinghere we should write something
;;
;; [^note]: And _here_ a **lenghty** [explanation](/foo/bar).

123

;; block with no sidenotes block with no sidenotes block with no sidenotes  block with no sidenotes block with no sidenotes
;; lock with no sidenotes block with no sidenotes block with no sidenotes  block with no sidenotes block with no sidenotes
;; block with no sidenotes block with no sidenotes block with no sidenotes  block with no sidenotes block with no sidenotes block with no sidenotes block with no sidenotes block with no sidenotes  block with no sidenotes block with no sidenotes

(comment
  (-> (parser/parse-file {:doc? true} "notebooks/sidenotes.clj")
      :sidenotes? )

  )
