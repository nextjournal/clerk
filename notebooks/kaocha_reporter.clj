(ns kaocha-reporter
  {:nextjournal.clerk/visibility {:code :hide :result :hide}
   :nextjourna.clerk/no-cache true}
  (:require [kaocha.report :as r]
            [nextjournal.clerk :as clerk]
            [nextjournal.clerk.builder-ui :as builder-ui]
            [nextjournal.clerk.analyzer :as clerk.analyzer]
            [nextjournal.clerk.viewer :as viewer]
            [clojure.test :as t :refer [is]]
            [kaocha.repl]
            [kaocha.config :as config]
            [kaocha.api :as kaocha]))

(t/deftest a-test
  (t/testing "should pass"
    (is (= 1 1))))

(t/deftest b-test
  (t/testing "should fail"
    (is (= 1 2))))

(defonce !test-run-events (atom []))
(defonce !test-report-state (atom {}))

(defn reset-state! []
  (reset! !test-run-events [])
  (reset! !test-report-state {})
  (clerk/recompute!))

(defn test-plan->test-nss [test-plan]                       ;;: {:name :ns :file :test-vars}
  (map
   (fn [{:kaocha.ns/keys [name ns] :kaocha.test-plan/keys [tests]}]
     {:ns ns
      :name name
      :state :loading
      :file (clerk.analyzer/ns->file ns)
      :test-vars (map (fn [{:kaocha.testable/keys [meta] :kaocha.var/keys [name var]}]
                        (assoc meta :var var :name name :state :loading)) tests)})
   (-> test-plan :kaocha.test-plan/tests first :kaocha.test-plan/tests)))

(defn ->test-var [e] (-> e :kaocha/testable :kaocha.var/var))
(defn ->test-ns-name [e] (-> e :kaocha/testable :kaocha.ns/name))
(defn update-test-ns [state nsn f]
  (update state
          :test-nss
          (partial into []
                   (map #(cond-> % (= nsn (ns-name (:ns %))) f)))))
(defn update-test-var [state var f]
  (update-test-ns state
                  (-> var symbol namespace symbol)
                  #(update % :test-vars
                           (partial into []
                                    (map (fn [{:as m test-var :var}]
                                           (cond-> m (= test-var var) f)))))))

#_ (ns-unmap *ns* 'build-test-state )
(defmulti build-test-state (fn bts-dispatch [_state {:as _event :keys [type]}] type))

(defmethod build-test-state :begin-test-suite [state {:as event :kaocha/keys [test-plan]}]
  (println :E (:type event))
  (swap! !test-run-events conj event)
  (assoc state :test-nss (test-plan->test-nss test-plan)))

(defmethod build-test-state :begin-test-ns [state event]
  (println :E (:type event))
  (swap! !test-run-events conj event)
  (update-test-ns state (->test-ns-name event) #(assoc % :state :executing)))

(defmethod build-test-state :begin-test-var [state {:as event :keys [var]}]
  (println :E (:type event))
  (swap! !test-run-events conj event)
  (update-test-var state (->test-var event) #(assoc % :state :executing)))

(defmethod build-test-state :pass [state event]
  (println :E (:type event))
  (swap! !test-run-events conj event)
  (update-test-var state (->test-var event) #(assoc % :state :done)))

(defmethod build-test-state :fail [state event]
  (println :E (:type event))
  (swap! !test-run-events conj event)
  (update-test-var state (->test-var event) #(assoc % :state :failed)))

(defmethod build-test-state :error [state event]
  (println :E (:type event))
  (swap! !test-run-events conj event)
  (update-test-var state (->test-var event) #(assoc % :state :errored)))

(defmethod build-test-state :kaocha.type.var/zero-assertions [state event]
  (println :E (:type event))
  (swap! !test-run-events conj event)
  state)

(defmethod build-test-state :end-test-var [state event]
  (println :E (:type event))
  (swap! !test-run-events conj event)
  state)

(some (some-fn #{:errored} #{:failed}) [nil nil true :failed :errored])

(defmethod build-test-state :end-test-ns [state event]
  (println :E (:type event))
  (swap! !test-run-events conj event)
  (update-test-ns state (->test-ns-name event)
                  (fn [{:as test-ns :keys [test-vars]}]
                    (assoc test-ns :state
                           (as-> (map :state test-vars) ss
                             (or (some #{:errored} ss) (some #{:failed} ss) :pass))))))

(defmethod build-test-state :end-test-suite [state event]
  (println :E (:type event))
  (swap! !test-run-events conj event)
  state)

(defmethod build-test-state :summary [state event]
  (println :E (:type event))
  (swap! !test-run-events conj event)
  state)

(defn notebook-reporter [event]
  (swap! !test-report-state #(build-test-state % event))
  (clerk/recompute!))

(defn bg-class [state]
  (case state
    :done "bg-green-100"
    :failed "bg-red-100"
    :errored "bg-red-100"
    "bg-slate-100"))

(def failed-status [:div.font-bold "❌"])

(defn test-var-badge [{:keys [name state]}]
  [:div.mb-2.rounded-md.border.border-slate-300.px-4.py-3.font-sans.shadow
   {:class (bg-class state)}
   [:div.flex.justify-between.items-center
    [:div.flex.items-center.truncate.mr-2
     [:div.mr-2
      (case state
        (:loading :executing) (builder-ui/spinner-svg)
        :failed failed-status
        :errored (builder-ui/error-svg)
        :done (builder-ui/checkmark-svg))]
     [:span.text-sm.mr-1 (case state
                           :loading "Loading"
                           :executing "Running"
                           :done "Pass"
                           :queued "Queued"
                           :failed "Failed"
                           :errored "Errored"
                           (str "unexpected state `" (pr-str state) "`"))]
     [:div.text-sm.font-medium.leading-none.truncate
      name]]]])

(defn test-ns-badge [{:keys [name state file ns test-vars]}]
  (println :test-ns-badge name state file)
  [:<>
   [:div.p-1.mt-2
    [:div.rounded-md.border.border-slate-300.px-4.py-3.font-sans.shadow
     {:class (bg-class state)}
     [:div.flex.justify-between.items-center
      [:div.flex.items-center.truncate.mr-2
       [:div.mr-2
        (case state
          (:loading :executing) (builder-ui/spinner-svg)
          :done (builder-ui/checkmark-svg)
          :failed failed-status
          :errored (builder-ui/error-svg))]

       [:span.text-sm.mr-1 (case state
                             :loading "Loading"
                             :executing "Running"
                             :done "Done"
                             :queued "Queued"
                             :errored "Errored"
                             (str "unexpected state `" (pr-str state) "`"))]
       [:div.text-sm.font-medium.leading-none.truncate
        file]]]]
    (into [:div.ml-5.mt-2] (map test-var-badge) test-vars)]])

(def test-ns-viewer {:transform-fn (viewer/update-val (comp viewer/html test-ns-badge))})

(def test-suite-viewer
  {:transform-fn (viewer/update-val (fn [{:keys [test-nss]}]
                                      (map (partial viewer/with-viewer test-ns-viewer) test-nss)))
   :render-fn '(fn [test-nss opts]
                 (if (seq test-nss)
                   (v/html (into [:div.flex.flex-col.pt-2] (v/inspect-children opts) test-nss))
                   (v/html [:h5 [:em.slate-100 "Waiting for tests to run..."]])))})

{::clerk/visibility {:code :show :result :hide}}
;; Evaluate the commented form to run tests
(comment
  (do (reset-state!)
      (kaocha.repl/run :my-suite
                       {:reporter [notebook-reporter]
               :capture-output? false
               :tests [{:id :my-suite
                        :ns-patterns ["tests" "kaocha-reporter"]
                        :test-paths ["notebooks/tests.clj"
                                     "notebooks/kaocha_reporter.clj"]}]})))

{::clerk/visibility {:code :hide :result :show}}
(clerk/with-viewer test-suite-viewer @!test-report-state)

{::clerk/visibility {:code :hide :result :hide}}
(comment

  (clerk/clear-cache!)

  (def cfg
    {:reporter [notebook-reporter]
     :capture-output? false
     :tests [{:id :my-suite
              :ns-patterns ["tests" "kaocha-reporter"]
              :test-paths ["notebooks/tests.clj"
                           "notebooks/kaocha_reporter.clj"]}]})

  (kaocha.repl/config cfg)
  ;; run tests!
  (reset-state!)


  (do
    (reset! !test-run-events [])
    (reset! !test-report-state {})
    (kaocha.repl/run :my-suite cfg))

  ;; see what's going on
  (test-plan->test-nss (kaocha.repl/test-plan cfg))

  (reset! !test-report-state {:test-nss (test-plan->test-nss (kaocha.repl/test-plan cfg))})

  (map :type @!test-run-events)

  (-> @!test-report-state)


  (set! *print-namespace-maps* false)

  (defn get-event [type]
    (some #(when (= type (:type %)) %)
          @!test-run-events))



  (update-test-var @!test-report-state
                   (-> (get-event :pass) :kaocha/testable :kaocha.var/var)
                   #(assoc % :state :passed))

  (-> (get-event :begin-test-ns)
      :kaocha/testable :kaocha.ns/name type

      ))
