(ns scratch2
  (:require [nextjournal.clerk :as clerk]))
^{::clerk/viewer clerk/row}
(def x 1 #_[1 2 3])

;; (clerk/row #:nextjournal{:value
;;                          #:nextjournal.clerk{:var-from-def #'scratch2/x,
;;                                              :var-snapshot [:div 2 3]},
;;                          :blob-id "5dtJy5qi3DZMithtJ9ysCbQrMYySma",
;;                          :viewer nextjournal.clerk/row})

;; ;; value+viewer
;; #:nextjournal{:value
;;               (#:nextjournal.clerk{:var-from-def #'scratch2/x,
;;                                    :var-snapshot 3}),
;;               :viewer nextjournal.clerk.viewer/row-viewer}
