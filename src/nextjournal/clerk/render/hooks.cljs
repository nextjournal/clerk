(ns nextjournal.clerk.render.hooks
  (:require ["d3-require" :as d3-require]
            ["react" :as react]
            [reagent.ratom]
            ["use-sync-external-store/shim" :refer [useSyncExternalStore]]))

;; a type for wrapping react/useState to support reset! and swap!
(deftype WrappedState [st]
  IIndexed
  (-nth [_coll i] (aget st i))
  (-nth [_coll i nf] (or (aget st i) nf))
  IDeref
  (-deref [^js _this] (aget st 0))
  IReset
  (-reset! [^js _this new-value]
    ;; `constantly` here ensures that if we reset state to a fn,
    ;; it is stored as-is and not applied to prev value.
    ((aget st 1) (constantly new-value))
    new-value)
  ISwap
  (-swap! [_this f] ((aget st 1) f))
  (-swap! [_this f a] ((aget st 1) #(f % a)))
  (-swap! [_this f a b] ((aget st 1) #(f % a b)))
  (-swap! [_this f a b xs] ((aget st 1) #(apply f % a b xs))))

(defn- as-array [x] (cond-> x (not (array? x)) to-array))

(defn use-memo
  "React hook: useMemo. Defaults to an empty `deps` array."
  ([f] (react/useMemo f #js[]))
  ([f deps] (react/useMemo f (as-array deps))))

(defn use-callback
  "React hook: useCallback. Defaults to an empty `deps` array."
  ([x] (use-callback x #js []))
  ([x deps] (react/useCallback x (to-array deps))))

(defn- wrap-effect
  ;; utility for wrapping function to return `js/undefined` for non-functions
  [f] #(let [v (f)] (if (fn? v) v js/undefined)))

(defn use-effect
  "React hook: useEffect. Defaults to an empty `deps` array.
   Wraps `f` to return js/undefined for any non-function value."
  ([f] (react/useEffect (wrap-effect f) #js[]))
  ([f deps] (react/useEffect (wrap-effect f) (as-array deps))))

(defn use-layout-effect
  "React hook: useLayoutEffect. Defaults to an empty `deps` array.
   Wraps `f` to return js/undefined for any non-function value."
  ([f] (react/useLayoutEffect (wrap-effect f) #js[]))
  ([f deps] (react/useLayoutEffect (wrap-effect f) (as-array deps))))

(defn use-state
  "React hook: useState. Can be used like react/useState but also behaves like an atom."
  [init]
  (WrappedState. (react/useState init)))

(defn- specify-atom! [ref-obj]
  (specify! ref-obj
    IDeref
    (-deref [^js this] (.-current this))
    IReset
    (-reset! [^js this new-value]
      (set! (.-current this) new-value)
      new-value)
    ISwap
    (-swap!
      ([o f] (reset! o (f o)))
      ([o f a] (reset! o (f o a)))
      ([o f a b] (reset! o (f o a b)))
      ([o f a b xs] (reset! o (apply f o a b xs))))))

(defn use-ref
  "React hook: useRef. Can also be used like an atom."
  ([] (use-ref nil))
  ([init] (specify-atom! (react/useRef init))))

(defn ^:private eval-fn
  "Invoke (f x) if f is a function, otherwise return f"
  [f x]
  (if (fn? f)
    (f x)
    f))

(defn use-force-update []
  (-> (react/useReducer inc 0)
      (aget 1)))

(defn use-state-with-deps
  ;; see https://github.com/peterjuras/use-state-with-deps/blob/main/src/index.ts
  "React hook: like `use-state` but will reset state to `init` when `deps` change.
  - init may be a function, receiving previous state
  - deps will be compared using clojure ="
  [init deps]
  (let [!state (use-ref
                (use-memo
                 #(eval-fn init nil)))
        !prev-deps (use-ref deps)
        _ (when-not (= deps @!prev-deps)
            (reset! !state (eval-fn init @!state))
            (reset! !prev-deps deps))
        force-update! (use-force-update)
        update-fn (use-callback
                   (fn [x]
                     (let [prev-state @!state
                           next-state (eval-fn x prev-state)]
                       (when (not= prev-state next-state)
                         (reset! !state next-state)
                         (force-update!))
                       next-state)))]
    (WrappedState. #js[@!state update-fn])))


(defn use-sync-external-store [subscribe get-snapshot]
  (useSyncExternalStore subscribe get-snapshot))

(defn use-watch
  "Hook for reading value of an IWatchable. Compatible with reading Reagent reactions non-reactively."
  [x]
  (let [id (use-callback #js{})]
    (use-sync-external-store
     (use-callback
      (fn [changed!]
        (add-watch x id (fn [_ _ _ _] (changed!)))
        #(remove-watch x id))
      #js[x])
     #(binding [reagent.ratom/*ratom-context* nil] @x))))

(defn use-error-handler []
  (let [[_ set-error] (use-state nil)]
    (use-callback (fn [error]
                    (set-error (fn [] (throw error))))
                  [set-error])))

(defn use-promise
  "React hook which resolves a promise and handles errors."
  [p]
  (let [handle-error (use-error-handler)
        !state (use-state nil)]
    (use-effect (fn []
                  (-> p
                      (.then #(reset! !state %))
                      (.catch handle-error)))
                #js [])
    @!state))

(defn ^js use-d3-require [package]
  (let [p (react/useMemo #(apply d3-require/require
                                 (cond-> package
                                   (string? package)
                                   list))
                         #js[(str package)])]
    (use-promise p)))
