(global-set-key (kbd "<f13>")
                (lambda ()
                  (interactive)
                  (shell-command "/Users/mk/bin/reload_brave.sh")))

(global-set-key (kbd "<f14>")
                (lambda ()
                  (interactive)
                  (let
                      ((filename
                        (buffer-file-name)))
                    (when filename
                      (cider-interactive-eval
                       (concat "(nextjournal.clerk/show! \"" filename "\")"))))))

(provide 'clerk)
