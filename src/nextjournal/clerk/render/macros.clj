(ns nextjournal.clerk.render.macros)

(defmacro sci-copy-nss [& nss]
  (into {} (for [[_ ns] nss]
             [(list 'quote ns) `(sci.core/copy-ns ~ns (sci.core/create-ns '~ns))])))

#_(macroexpand '(sci-copy-nss 'nextjournal.clerk.render.hoooks
                              'nextjournal.clerk.render.code))
