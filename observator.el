(global-set-key (kbd "<f13>")
                (lambda ()
                  (interactive)
                  (let
                      ((filename
                        (buffer-file-name)))
                    (when filename
                      (cider-interactive-eval
                       (concat "(observator.core/code->panel observator.core/panel (slurp \"" filename "\"))"))))))

(provide 'observator)
