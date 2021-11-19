;; # Exercise 1.44: The double pendulum

;; This namespace explores [Exercise
;; 1.44](https://tgvaughan.github.io/sicm/chapter001.html#Exe_1-44) from Sussman
;; and Wisdom's [Structure and Interpretation of Classical
;; Mechanics](https://tgvaughan.github.io/sicm/), using
;; the [SICMUtils](https://github.com/sicmutils/sicmutils) Clojure library and
;; the Clerk rendering environment.

(ns double-pendulum
  (:refer-clojure
   :exclude [+ - * / partial ref zero? numerator denominator compare = run!])
  (:require [aerial.hanami.common :as hanami-common]
            [aerial.hanami.templates :as hanami-templates]
            [notespace-sicmutils.hanami-extras :as hanami-extras]
            [nextjournal.clerk :as clerk]
            [sicmutils.env :as e :refer :all]))

;; ## Lagrangian
;;
;; Start with a coordinate transformation from `theta1`, `theta2` to rectangular
;; coordinates. We'll generate our Lagrangian by composing this with an rectangular
;; Lagrangian with the familiar form of `T - V`.

(defn angles->rect [l1 l2]
  (fn [[t [theta1 theta2]]]
    (let [x1 (* l1 (sin theta1))
          y1 (- (* l1 (cos theta1)))
          x2 (+ x1 (* l2 (sin (+ theta1 theta2))))
          y2 (- y1 (* l2 (cos (+ theta1 theta2))))]
      (up x1 y1 x2 y2))))

;; `T` describes the sum of the kinetic energy of two particles in rectangular
;; coordinates.

(defn T [m1 m2]
  (fn [[_ _ [xdot1 ydot1 xdot2 ydot2]]]
    (+ (* (/ 1 2) m1 (+ (square xdot1)
                        (square ydot1)))
       (* (/ 1 2) m2 (+ (square xdot2)
                        (square ydot2))))))


;; `V` describes a uniform gravitational potential with coefficient `g`, acting
;; on two particles with masses of, respectively, `m1` and `m2`. Again, this is
;; written in rectangular coordinates.

(defn V [m1 m2 g]
  (fn [[_ [_ y1 _ y2]]]
    (+ (* m1 g y1)
       (* m2 g y2))))

;; Form the rectangular Lagrangian `L` by subtracting `(V m1 m2 g)` from `(T m1 m2)`:

(defn L-rect [m1 m2 g]
  (- (T m1 m2)
     (V m1 m2 g)))

;; Form the final Langrangian in generalized coordinates (the angles of each
;; segment) by composing `L-rect` with a properly transformed `angles->rect`
;; coordinate transform!

(defn L-double-pendulum [m1 m2 l1 l2 g]
  (compose (L-rect m1 m2 g)
           (F->C
            (angles->rect l1 l2))))

;; The Lagrangian is big and hairy:

(def symbolic-L
  ((L-double-pendulum 'm_1 'm_2 'l_1 'l_2 'g)
   (up 't
       (up 'theta_1 'theta_2)
       (up 'theta_1dot 'theta_2dot))))

;; Let's simplify that:

(simplify symbolic-L)

;; Better yet, let's render it as LaTeX, and create a helper function,
;; `render-eq` to make it easier to render simplified equations:

(def render-eq
  (comp clerk/tex ->TeX simplify))

(render-eq symbolic-L)

;; And here are the equations of motion for the system:

(let [L (L-double-pendulum 'm_1 'm_2 'l_1 'l_2 'g)]
  (binding [sicmutils.expression.render/*TeX-vertical-down-tuples* true]
    (render-eq
     (((Lagrange-equations L)
       (up (literal-function 'theta_1)
           (literal-function 'theta_2)))
      't))))

;; What do these mean?
;;
;; - the system has two degrees of freedom: $\theta_1$ and $\theta_2$.

;; - at any point `t` in time, the two equations above, full of first and second
;; - order derivatives of the position functions, will stay true
;; - the system can use these equations to simulate the system, one tick at a time.

;; ## Simulation
;;
;; Next, let's run a simulation using those equations of motion and collect data
;; on each coordinate's evolution.
;;
;; Here are the constants specified in exercise 1.44:
;;
;; masses in kg:

(def m1 1.0)
(def m2 3.0)

;; lengths in meters:

(def l1 1.0)
(def l2 0.9)

;; `g` in units of m/s^2:

(def g 9.8)

;; And two sets of initial pairs of `theta1`, `theta2` angles corresponding to
;; chaotic and regular initial conditions:

(def chaotic-initial-q (up (/ Math/PI 2) Math/PI))
(def regular-initial-q (up (/ Math/PI 2) 0.0))

;; Composing `Lagrangian->state-derivative` with `L-double-pendulum` produces
;; a state derivative that we can use with our ODE solver:

(def state-derivative
  (compose
   Lagrangian->state-derivative
   L-double-pendulum))

;; Finally, two default parameters for our simulation. We'll record data in
;; steps of 0.01 seconds, and simulate to a horizon of 50 seconds.

(def step 0.01)
(def horizon 50)

;; `run!` will return a sequence of 5001 states, one for each measured point in
;; the simulation. The smaller-arity version simply passes in default masses and
;; lengths, but you can override those with the larger arity version if you like.

;; (The interface here could use some work: `integrate-state-derivative` tidies
;; this up a bit, but I want it out in the open for now.)

(defn run!
  ([step horizon initial-coords]
   (run! step horizon l1 l2 m1 m2 g initial-coords))
  ([step horizon l1 l2 m1 m2 g initial-coords]
   (let [collector     (atom (transient []))
         initial-state (up 0.0
                           initial-coords
                           (up 0.0 0.0))]
     ((evolve state-derivative m1 m2 l1 l2 g)
      initial-state
      step
      horizon
      {:compile? true
       :epsilon 1.0e-13
       :observe (fn [t state]
                  (swap!
                   collector conj! state))})
     (persistent! @collector))))

;; Run the simulation for each set of initial conditions and show the final
;; state. Chaotic first:

(def raw-chaotic-data
  (run! step horizon chaotic-initial-q))

;; Looks good:

(peek raw-chaotic-data)

;; Next, the regular initial condition:

(def raw-regular-data
  (run! step horizon regular-initial-q))

;; Peek at the final state:

(peek raw-regular-data)

;; ## Measurements, Data Transformation

;; Next we'll chart the measurements trapped in those sequences of state tuples.

;; The exercise asks us to graph the energy of the system as a function of time.
;; Composing `Lagrangian->energy` with `L-double-pendulum` gives us a new
;; function (of a state tuple!) that will return the current energy in the
;; system.:

(def L-energy
  (compose
   Lagrangian->energy
   L-double-pendulum))

;; `energy-monitor` returns a function of `state` that stores an initial energy
;; value in its closure, and returns the delta of each new state's energy as
;; compared to the original.

(defn energy-monitor [energy-fn initial-state]
  (let [initial-energy (energy-fn initial-state)]
    (fn [state]
      (- (energy-fn state) initial-energy))))

;; Finally, the large `transform-data` function. The charting library we'll use
;; likes Clojure dictionaries; `transform-data` turns our raw data into a
;; sequence of dictionary with all values we might care to explore. This
;; includes:

;; - the generalized coordinate angles
;; - the generalized velocities of those angles
;; - the rectangular coordinates of each pendulum bob
;; - `:d-energy`, the error in energy at each point in the simulation

;; Here is `transform-data`:

(defn transform-data [xs]
  (let [energy-fn (L-energy m1 m2 l1 l2 g)
        monitor   (energy-monitor energy-fn (first xs))
        xform     (angles->rect l1 l2)
        pv        (principal-value Math/PI)]
    (map (fn [[t [theta1 theta2] [thetadot1 thetadot2] :as state]]
           (let [[x1 y1 x2 y2] (xform state)]
             {:t t
              :theta1 (pv theta1)
              :x1 x1
              :y1 y1
              :theta2 (pv theta2)
              :x2 x2
              :y2 y2
              :thetadot1 thetadot1
              :thetadot2 thetadot2
              :d-energy (monitor state)}))
         xs)))

;; The following forms transform the raw data for each initial condition and
;; bind the results to `chaotic-data` and `regular-data` for exploration.

(def chaotic-data
  (doall
   (transform-data raw-chaotic-data)))

(def regular-data
  (doall
   (transform-data raw-regular-data)))

;; Here's the final, transformed chaotic state:

(last chaotic-data)

;; And the similar regular state:

(last regular-data)

;; ## Data Visualization Utilities

;; [Hanami](https://github.com/jsa-aerial/hanami)'s templates allow us to create
;; a [Vega-Lite](https://vega.github.io/vega-lite/) spec for visualizing the
;; system.

;; I am not a pro here, but this does the trick for now.

;; First, a function to transform the dictionaries we generated above into a
;; sequence of `x, y` coordinates tagged with distinct IDs for each pendulum
;; bob's points:

(defn points-data [data]
  (mapcat (fn [{:keys [t x1 y1 x2 y2]}]
            [{:t  t
              :x  x1
              :y  y1
              :id :p1}
             {:t  t
              :x  x2
              :y  y2
              :id :p2}])
          data))

;; `segments-data` generates endpoints of the pendulum segments, bob-to-bob:

(defn segments-data [data]
  (mapcat (fn [{:keys [t x1 y1 x2 y2]}]
            [{:t  t
              :x 0
              :y 0
              :x2 x1
              :y2 y1
              :id :p1}
             {:t  t
              :x  x1
              :y  y1
              :x2 x2
              :y2 y2
              :id :p2}])
          data))

;; `animation-spec` returns a chart with an attached `t` animation slider that
;; graphs the position of each pendulum bob through time, with the actual
;; pendulum itself overlaid. Scroll down a bit to see it in action.

(defn animation-spec [data]
  (hanami-common/xform
   hanami-templates/layer-chart
   :LAYER [(hanami-common/xform
            hanami-templates/point-chart
            :DATA (points-data data)
            :COLOR {:field :id :type :nominal}
            :SIZE
            {:condition {:test  "abs(selected_t - datum['t']) < 0.00001"
                         :value 200}
             :value     5}
            :OPACITY
            {:condition {:test  "abs(selected_t - datum['t']) < 0.00001"
                         :value 1}
             :value     0.3}
            :SELECTION {:selected {:fields [:t]
                                   :type   :single
                                   :bind   {:t {:min   step
                                                :max   (- horizon step)
                                                :input :range
                                                :step  step}}}})
           (hanami-common/xform
            hanami-extras/rule-chart
            :DATA (segments-data data)
            :COLOR {:field :id :type :nominal}
            :OPACITY {:condition {:test  "abs(selected_t - datum['t']) < 0.00001"
                                  :value 1}
                      :value     0})]))

;; `k-vs-time` generates a chart spec that monitors some particular key in the
;; transformed state values as, you guessed it, a function of time:

(defn k-vs-time
  "Returns a chart of the specified `k` vs `:t` in the supplied sequence of
  points."
  ([data k]
   (k-vs-time data k false))
  ([data k animated?]
   (let [size (if animated?
                {:condition
                 {:test "abs(selected_t - datum['t']) < 0.00001"
                  :value 200}
                 :value 5}
                {:value 5})]
     (apply hanami-common/xform
            hanami-templates/point-chart
            [:DATA data
             :X :t
             :Y k
             :SIZE size]))))

;; ## Visualizations

;; With those tools in hand, let's make some charts. I'll call this first chart
;; the `system-inspector`; this function will return a chart that will let us
;; evolve the system with a time slider and monitor both angles, the energy
;; error, and the pendulum bob itself as it evolves through time.

(defn with-domain [chart domain]
  (update-in chart
             [:encoding :y]
             assoc
             :scale {:domain domain}))

(defn system-inspector [data]
  (hanami-common/xform
   hanami-templates/vconcat-chart
   :VCONCAT [(hanami-common/xform
              hanami-templates/hconcat-chart
              :HCONCAT [(animation-spec data)
                        (k-vs-time data :d-energy true)])
             (hanami-common/xform
              hanami-templates/hconcat-chart
              :HCONCAT [(-> (k-vs-time data :theta1 true)
                            (with-domain [(- Math/PI) Math/PI]))
                        (-> (k-vs-time data :theta2 true)
                            (with-domain [(- Math/PI) Math/PI]))])]))

;; Here's a system monitor for the chaotic initial condition:

(clerk/vl
 (system-inspector chaotic-data))

;; And again for the regular initial condition:

(clerk/vl
 (system-inspector regular-data))

;; ## Generalized coordinates, velocities

;; `angles-plot` generates a plot, with no animation, showing both theta angles
;; and their associated velocities:

(defn angles-plot [data]
  (hanami-common/xform
   hanami-templates/hconcat-chart
   :HCONCAT [(hanami-common/xform
              hanami-templates/vconcat-chart
              :VCONCAT
              [(-> (k-vs-time data :theta1)
                   (with-domain [(- Math/PI) Math/PI]))
               (-> (k-vs-time data :theta2)
                   (with-domain [(- Math/PI) Math/PI]))])
             (hanami-common/xform
              hanami-templates/vconcat-chart
              :VCONCAT
              [(k-vs-time data :thetadot1)
               (k-vs-time data :thetadot2)])]))

;; Here are the angles for the chaotic initial condition:

(clerk/vl
 (angles-plot chaotic-data))

;; And the regular initial condition:

(clerk/vl
 (angles-plot regular-data))

;; ## Double Double Pendulum!

;; No visualizations here yet, but the code works well.

(defn L-double-double-pendulum [m1 m2 l1 l2 g]
  (fn [[t [thetas1 thetas2] [qdots1 qdots2]]]
    (let [s1 (up t thetas1 qdots1)
          s2 (up t thetas2 qdots2)]
      (+ ((L-double-pendulum m1 m2 l1 l2 g) s1)
         ((L-double-pendulum m1 m2 l1 l2 g) s2)))))

(def dd-state-derivative
  (compose
   Lagrangian->state-derivative
   L-double-double-pendulum))

(defn safe-log [x]
  (if (< x 1e-60)
    -138.0
    (Math/log x)))

(defn divergence-monitor []
  (let [pv (principal-value Math/PI)]
    (fn [[t [thetas1 thetas2]]]
      (safe-log
       (Math/abs
        (pv
         (- (nth thetas1 1)
            (nth thetas2 1))))))))

(defn run-double-double!
  "Two different initializations, slightly kicked"
  [step horizon initial-q1]
  (let [initial-q2    (+ initial-q1 (up 0.0 1e-10))
        initial-qdot  (up 0.0 0.0)
        initial-state (up 0.0
                          (up initial-q1 initial-q2)
                          (up initial-qdot initial-qdot))
        collector     (atom (transient []))]
    ((evolve dd-state-derivative m1 m2 l1 l2 g)
     initial-state
     step
     horizon
     {:compile? true
      :epsilon 1.0e-13 ; = (max-norm 1.e-13)
      :observe (fn [t state]
                 (swap! collector conj! state))})
    (persistent! @collector)))

(def raw-dd-chaotic-data
  (run-double-double! step horizon chaotic-initial-q))

;; Looks good:

(peek raw-dd-chaotic-data)

;; Next, the regular initial condition:

(def raw-dd-regular-data
  (run-double-double! step horizon regular-initial-q))

;; Peek at the final state:

(peek raw-dd-regular-data)

#_(nextjournal.clerk/show! "notebooks/double_pendulum.clj")
