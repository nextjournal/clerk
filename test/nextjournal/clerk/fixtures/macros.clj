(ns nextjournal.clerk.fixtures.macros)

;; see n.c.analyzer-test/missing-hashes
(defmacro emit-nonsense []
  (let [sym (gensym "my-var-")]
    (intern *ns* sym :nonsense)
    `~(symbol (name (ns-name *ns*)) (name sym))))
