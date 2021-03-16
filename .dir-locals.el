;;; Directory Local Variables
;;; For more information see (info "(emacs) Directory Variables")
((nil . ((eval . ((lambda ()
                    (when (not (featurep 'observator))
                      (let ((init-file-path (expand-file-name "observator.el" default-directory)))
                        (when (file-exists-p init-file-path)
                          (load init-file-path)
                          (require 'observator))))))))))
