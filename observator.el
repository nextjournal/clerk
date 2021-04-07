(global-set-key (kbd "<f13>")
                (lambda ()
                  (interactive)
                  (let
                      ((filename
                        (buffer-file-name)))
                    (when filename
                      (cider-interactive-eval
                       (concat "(observator.core/file->panel observator.core/panel \"" filename "\")"))))))

(provide 'observator)
