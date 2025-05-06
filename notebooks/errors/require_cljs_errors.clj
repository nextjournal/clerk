;; # 💣 Render Errors

(ns require-cljs-errors
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as v]))

(clerk/with-viewer {:require-cljs true
                    :render-fn 'errors.render-botched/test} 1)

#_(try (/ 1 0) (catch Exception e (throw (ex-info "boom!" {:foo :bar} e))))

#_(clerk/show! "foo.clj")
