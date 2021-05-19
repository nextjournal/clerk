(global-set-key (kbd "<f12>")
                (lambda ()
                  (interactive)
                  (let
                      ((filename
                        (buffer-file-name)))
                    (when filename
                      (cider-interactive-eval
                       (concat "(nextjournal.clerk/show! \"" filename "\")"))))))

(provide 'clerk)
