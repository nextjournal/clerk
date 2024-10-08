;; # 😩 Errors

(ns boundaries
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as v]))

#_boom

#_(clerk/with-viewer {:require-cljs true
                      :render-fn 'errors.render/test} 1)

#_(try (/ 1 0) (catch Exception e (throw (ex-info "boom!" {:foo :bar} e))))

#_(clerk/show! "foo.clj")
