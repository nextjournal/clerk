(require '[nextjournal.clerk :as clerk])

(clerk/eval-cljs-str
 (pr-str
  '(do (def p (-> (js/fetch "http://localhost:7777/")
                  (.then (fn [resp] (.text resp)))))
       (def state (reagent/atom nil))
       (.then p #(js/setTimeout (fn [] (reset! state %)) 1000))
       (defn foo []
         (v/html [:pre (or @state
                           "Loading...")])))))

(comment
  (nextjournal.clerk/clear-cache!)
  )

(clerk/with-viewer
  {:transform-fn clerk/mark-presented :render-fn 'user/foo}
  [1 2 3 4 5 6 8 9 10 11 12 13 14])
