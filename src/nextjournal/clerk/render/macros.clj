(ns nextjournal.clerk.render.macros)

(defmacro sci-copy-nss [& nss]
  (into {} (for [[_ ns] nss]
             `[(quote ~ns)
               (if (empty? (ns-publics (quote ~ns)))
                 (throw (ex-info (str "Namespace " (quote ~ns) " is empty, forgot to require?") {:ns (quote ~ns)}))
                 (sci.core/copy-ns ~ns (sci.core/create-ns '~ns)))])))

#_(macroexpand '(sci-copy-nss 'nextjournal.clerk.render.hoooks
                              'nextjournal.clerk.render.code))
