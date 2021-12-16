;;; Directory Local Variables
;;; For more information see (info "(emacs) Directory Variables")
((nil . ((cider-clojure-cli-global-options . "-A:demo")
         (use-bb-dev . t)
         (eval . ((lambda ()
                    (defun cider-jack-in-wrapper-function (orig-fun &rest args)
                      (if (and (boundp 'use-bb-dev) use-bb-dev)
                          (message "Use `bb dev` to start the development server, then `cider-connect` to the port it specifies.")
                        (apply orig-fun args)))

                    (advice-add 'cider-jack-in :around #'cider-jack-in-wrapper-function)
                    
                    (when (not (featurep 'clerk))
                      (let ((init-file-path (expand-file-name "clerk.el" default-directory)))
                        (when (file-exists-p init-file-path)
                          (load init-file-path)
                          (require 'clerk))))))))))
