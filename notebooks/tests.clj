;; # 🧪 Clerk Tests
^{:nextjournal.clerk/visibility :hide-ns}
(ns ^:nextjournal.clerk/no-cache tests
  (:require [nextjournal.clerk.config :as config]
            [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as viewer]
            [clojure.test :as t]))


(clerk/set-viewers!
 [{:name :tests :render-fn #(v/html (into [:div.flex.flex-col] (v/inspect-children %2) %1))}
  {:name :test :render-fn (fn [{:keys [form passes?]}]
                            (v/html [:div.inline-flex
                                     (if passes? '✅ '❌)
                                     [v/inspect (v/code-viewer form)]]))
   :fetch-fn (fn [_ x] x)}])


(defmacro tests [& body]
  (when config/*in-clerk*
    `(viewer/with-viewer* :tests
       ~(mapv #(viewer/with-viewer* :test
                 (hash-map :form (pr-str %) :passes? %)) body))))

#_(binding [config/*in-clerk* true]
    (macroexpand '(tests (t/is (= 42 (+ 39 3)))
                         (t/is (= 42 :answer-to-everything)))))

(tests (t/is (= 42 (+ 39 3)))
       (t/is (= 42 :answer-to-everything)))
