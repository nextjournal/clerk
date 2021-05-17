(global-set-key (kbd "<f13>")
                (lambda ()
                  (interactive)
                  (let
                      ((filename
                        (buffer-file-name)))
                    (when filename
                      (cider-interactive-eval
                       (concat "(nextjournal.clerk/show! \"" filename "\")"))))))

(provide 'clerk)
