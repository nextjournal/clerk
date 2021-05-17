;;; Directory Local Variables
;;; For more information see (info "(emacs) Directory Variables")
((nil . ((cider-clojure-cli-global-options . "-A:demo")
         (eval . ((lambda ()
                    (when (not (featurep 'clerk))
                      (let ((init-file-path (expand-file-name "clerk.el" default-directory)))
                        (when (file-exists-p init-file-path)
                          (load init-file-path)
                          (require 'clerk))))))))))
