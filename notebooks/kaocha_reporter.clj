;; # 🍵 Kaocha Test Report
(ns kaocha-reporter
  {:nextjournal.clerk/visibility {:code :hide :result :hide}
   :nextjourna.clerk/no-cache true}
  (:require [clojure.test :as t :refer [deftest testing is]]
            [kaocha.report :as r]
            [kaocha.repl]
            [kaocha.config :as config]
            [kaocha.api :as kaocha]
            [lambdaisland.deep-diff :as ddiff]
            [kaocha.matcher-combinators]
            [matcher-combinators.test]
            [nextjournal.clerk :as clerk]
            [nextjournal.clerk.builder-ui :as builder-ui]
            [nextjournal.clerk.analyzer :as clerk.analyzer]
            [nextjournal.clerk.viewer :as viewer]))

(deftest a-test
  (testing "In some context"
    (testing "should pass"
      (is (= 1 1))
      (testing "really"
        (is (= 2 2))))))

(deftest b-test
  (testing "should fail"
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
      :state :queued
      :file (clerk.analyzer/ns->file ns)
      ;; we're showing test-vars as they're reported to pass/fail. As an alternative we can show them from the beginning
      :test-vars [] #_(map (fn [{:kaocha.testable/keys [meta] :kaocha.var/keys [name var]}]
                             (assoc meta :var var :name name :state :queued)) tests)})
   (-> test-plan :kaocha.test-plan/tests first :kaocha.test-plan/tests)))

(defn initial-state [test-plan]
  {:test-nss (test-plan->test-nss test-plan)
   :seen-ctxs #{}})

(defn ->test-var-name [e] (-> e :kaocha/testable :kaocha.var/name))
(defn ->test-ns-name  [e]
  (or (-> e :kaocha/testable :kaocha.ns/name)
      (-> e :kaocha/testable :kaocha.var/name namespace symbol)))

(defn vec-update-if [pred f] (partial into [] (map (fn [item] (cond-> item (pred item) f)))))

(defn ->test-var-data
  ([event] (->test-var-data event :queued))
  ([{:as event
     {:kaocha.testable/keys [meta] :kaocha.var/keys [name var]} :kaocha/testable
     ex :kaocha.result/exception} status]
   (-> meta
       (merge (select-keys event [:actual :expected :message]))
       (assoc :var var :name name :state status)
       (cond-> ex (assoc :exception ex)))))

(defn update-test-ns [state nsn f]
  (update state :test-nss (vec-update-if #(= nsn (:name %)) f)))

(defn update-test-var [state varn f]
  (update-test-ns state
                  (-> varn namespace symbol)
                  (fn [test-ns] (update test-ns :test-vars (vec-update-if #(= varn (:name %)) f)))))

(defn update-contexts [{:as state :keys [seen-ctxs]} event]
  (let [ctxs (remove seen-ctxs t/*testing-contexts*)
        depth (count (filter seen-ctxs t/*testing-contexts*))
        ctx-items (map-indexed (fn [i c] {:type :ctx
                                          :ctx/text c
                                          :ctx/depth (+ depth i)}) (reverse ctxs))]
    (-> state
        (update :seen-ctxs into ctxs)
        (update-test-ns (->test-ns-name event)
                        #(update % :test-vars into ctx-items)))))

#_ (ns-unmap *ns* 'build-test-state )
(defmulti build-test-state (fn bts-dispatch [_state {:as _event :keys [type]}] type))

(defmethod build-test-state :begin-test-suite [_state {:as event :kaocha/keys [test-plan]}]
  (swap! !test-run-events conj event)
  (initial-state test-plan))

(defmethod build-test-state :begin-test-ns [state event]
  (swap! !test-run-events conj event)
  (update-test-ns state (->test-ns-name event) #(assoc % :state :executing)))

(defmethod build-test-state :begin-test-var [state {:as event :keys [var]}]
  (swap! !test-run-events conj event)
  #_ (update-test-var state (->test-var-name event) #(assoc % :state :executing))
  state)

(defmethod build-test-state :pass [state event]
  (swap! !test-run-events conj event)
  #_ (update-test-var state (->test-var-name event) #(assoc % :state :pass))
  (-> state
      (update-contexts event)
      (update-test-ns (->test-ns-name event) #(update % :test-vars conj (->test-var-data event :pass)))))

(defmethod build-test-state :fail [state event]
  (swap! !test-run-events conj event)
  #_ (update-test-var state (->test-var-name event) #(assoc % :state :failed))
  (-> state
      (update-contexts event)
      (update-test-ns (->test-ns-name event) #(update % :test-vars conj (->test-var-data event :failed)))))

(defmethod build-test-state :error [state event]
  (swap! !test-run-events conj event)
  #_ (update-test-var state (->test-var-name event) #(assoc % :state :errored))
  (-> state
      (update-contexts event)
      (update-test-ns (->test-ns-name event) #(update % :test-vars conj (->test-var-data event :errored)))))

(defmethod build-test-state :kaocha.type.var/zero-assertions [state event]
  (swap! !test-run-events conj event)
  state)

(defmethod build-test-state :end-test-var [state event]
  (swap! !test-run-events conj event)
  state)

(defmethod build-test-state :end-test-ns [state event]
  (swap! !test-run-events conj event)
  (update-test-ns state (->test-ns-name event)
                  (fn [{:as test-ns :keys [test-vars]}]
                    (assoc test-ns :state
                           (as-> (map :state test-vars) ss
                             (or (some #{:errored} ss) (some #{:failed} ss) :pass))))))

(defmethod build-test-state :end-test-suite [state event]
  (swap! !test-run-events conj event)
  state)

(defmethod build-test-state :summary [state event]
  (swap! !test-run-events conj event)
  (assoc state :summary (select-keys event [:pending :file :fail :line :error :pass :test])))

#_ 'the-kaocha-reporter
(defn notebook-reporter [event]
  (swap! !test-report-state #(build-test-state % event))
  (clerk/recompute!))

(defn bg-class [state]
  (case state
    :done "bg-green-100"
    :failed "bg-red-100"
    :errored "bg-red-100"
    "bg-slate-100"))


(defn status->icon [status]
  (case status
    (:queued :executing) (builder-ui/spinner-svg)
    :failed [:div "❌"]
    :errored (builder-ui/error-svg)
    :pass (builder-ui/checkmark-svg)))
(defn status->text [status]
  (case status
    :queued "Queued"
    :executing "Running"
    :pass "Pass"
    :failed "Failed"
    :errored "Errored"))

(defn test-var-badge [{:as tvar :keys [name state line expected actual exception] :ctx/keys [text depth]}]
  (if text
    [:div.text-slate-500 {:class (when (< 0 depth) (str "ml-" (* 4 depth)))} text]
    [:div.mb-2.rounded-md.border.border-slate-300.px-4.py-2.font-sans.shadow
     {:class (bg-class state)}
     [:div.flex.justify-between.items-center
      [:div.flex.items-center.truncate.mr-2
       [:div.mr-2 (status->icon state)]
       [:span.text-sm.mr-1 (status->text state)]
       [:div.text-sm.font-medium.leading-none.truncate (str name ":" line)]]]
     (when (= :failed state)
       ;; TODO: structural diff
       [:table
        [:tbody
         [:tr.hover:bg-red-100.leading-tight
          [:td.text-right.font-medium "expected:"]
          [:td.text-left (viewer/code (pr-str expected))]]
         [:tr.hover:bg-red-100.leading-tight
          [:td.text-right.font-medium "actual:"]
          [:td.text-left (viewer/code (pr-str actual))]]]])
     (when (= :errored state)
       [:div.mt-2.rounded-md.shadow-lg.border.border-gray-300.overflow-scroll {:style {:height "200px"}}
        (viewer/present exception)])]))

(defn test-ns-badge [{:keys [name state file ns test-vars]}]
  [:div.p-1.mt-2
   [:div.rounded-md.border.border-slate-300.px-4.py-3.font-sans.shadow
    {:class (bg-class state)}
    [:div.flex.justify-between.items-center
     [:div.flex.items-center.truncate.mr-2
      [:div.mr-2 (status->icon state)]
      [:span.text-sm.mr-1 (status->text state)]
      [:div.text-sm.font-semibold.leading-none.truncate file]]]]
   (into [:div.ml-5.mt-2] (map test-var-badge) test-vars)])

(def test-ns-viewer {:transform-fn (viewer/update-val (comp viewer/html test-ns-badge))})

(def test-suite-viewer
  {:transform-fn (comp viewer/mark-preserve-keys
                       (viewer/update-val (fn [state]
                                            (-> state
                                                (update :test-nss (partial map (partial viewer/with-viewer test-ns-viewer)))
                                                (update :summary #(-> % viewer/ensure-wrapped viewer/mark-presented))))))

   :render-fn '(fn [{:keys [test-nss summary]} opts]
                 (js/console.log :TNSS (:nextjournal/value test-nss) :SUMMARY summary)
                 (v/html
                  [:<>
                   (if-some [xs (seq (:nextjournal/value test-nss))]
                     (into [:div.flex.flex-col.pt-2] (v/inspect-children opts) xs)
                     [:h5 [:em.slate-100 "Waiting for tests to run..."]])
                   (when (:nextjournal/value summary)
                     [:h2 "Test Summary" [v/inspect summary]])]))})

{::clerk/visibility {:code :show :result :hide}}
;; Evaluate the commented form to run tests
(comment
  (kaocha.repl/run :unit {:reporter [notebook-reporter]}))

{::clerk/visibility {:code :hide :result :show}}
(clerk/with-viewer test-suite-viewer @!test-report-state)

{::clerk/visibility {:code :hide :result :hide}}
(comment
  (clerk/clear-cache!)

  ;; config for testing failures and errors
  (def cfg
    {:reporter [notebook-reporter]
     :capture-output? false
     :test-paths ["notebooks" "test"]
     :tests [{:id :my-suite
              :ns-patterns ["test|reporter"]
              :test-paths ["notebooks/tests.clj"
                           "notebooks/kaocha_reporter.clj"
                           #_
                           "test/nextjournal/clerk/analyzer_test.clj"]}]})
  ;; run tests!
  (kaocha.repl/run :my-suite cfg)

  (kaocha.repl/config)
  (kaocha.repl/config cfg)
  (test-plan->test-nss (kaocha.repl/test-plan))
  (test-plan->test-nss (kaocha.repl/test-plan cfg))
  (reset-state!)

  (reset! !test-report-state {:test-nss (test-plan->test-nss (kaocha.repl/test-plan cfg))})

  (map :line @!test-run-events)
  (-> @!test-report-state)
  (set! *print-namespace-maps* false)

  ;; inspect events
  (defn get-event [type]
    (some #(when (= type (:type %)) %)
          @!test-run-events))
  (-> (get-event :error)  )

  (nextjournal.clerk/clear-cache!))
