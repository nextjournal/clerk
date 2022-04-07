;; # 🧪 Clerk Tests
(ns ^:nextjournal.clerk/no-cache nextjournal.clerk.test
  (:require [nextjournal.clerk.config :as config]
            [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as viewer]
            [clojure.test :as t]
            [nextjournal.clerk.viewer :as v]))


(def test-viewer
  {:transform-fn (fn [{:keys [form result]}]
                   (clerk/html [:div.mb-2.last:mb-0
                                [:div.px-2.rounded.inline-flex.py-1
                                 {:class (case result :pass "bg-green-50" :fail "bg-red-50" nil "bg-slate-100")}
                                 [:div.mt-1.mr-2 (case result
                                                   :pass '✅
                                                   :fail '❌
                                                   nil '⌛️)]
                                 (clerk/code form)]]))})

(clerk/with-viewer test-viewer
  {:form '(t/is (= 42 (+ 39 3)))})

(clerk/with-viewer test-viewer
  {:form '(t/is (= 42 (+ 39 3))) :result :pass})

(clerk/with-viewer test-viewer
  {:form '(t/is (= 43 (+ 39 3))) :result :fail})

(def tests-viewer
  {:transform-fn (fn [tests]
                   (let [{:keys [pass fail] :or {pass 0 fail 0}} (frequencies (map :result tests))]
                     (clerk/html (into [:div.border-l-2.pl-4.-slate-300
                                        [:div.uppercase.tracking-wider.text-xs.font-sans.text-slate-500.mt-4.mb-2.flex.items-center
                                         [:span.ml-2 "Tests (" fail " of " (+ pass fail) " failed)"]]]
                                       (mapv (partial clerk/with-viewer test-viewer) tests)))))})


(clerk/with-viewer tests-viewer
  [{:form '(t/is (= 42 (+ 39 3))) :result :pass}
   {:form '(t/is (= 43 (+ 39 3))) :result :fail}])

(defmacro tests [& body]
  (when nextjournal.clerk.config/*in-clerk*
    `(clerk/with-viewer tests-viewer
       (mapv (fn [form# passed?#]
               {:form form# :result (if passed?# :pass :fail)})
             ~(mapv (fn [x#] `'~x#) body)
             ~(vec body)))))

(tests
 (t/is (= 42 (+ 39 3)))
 (t/is (= 42 :answer-to-everything)))

#_ TODO
#_(t/testing "let's compute an answer"
    (t/is (= 42 (+ 39 3)))
    (t/is (= 42 :answer-to-everything)))
