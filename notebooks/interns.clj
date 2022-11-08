;; # 🎭 Manually interned Vars
(ns interns)

(defn my-intern [x] (intern *ns* x 42))

(my-intern 'the-answer)

the-answer
