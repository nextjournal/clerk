(ns render-fns
  "An illustration on how to write Clerk render functions in a cljs file."
  (:require ["framer-motion" :as framer-motion :refer [motion]]))

(defn motion-div [props]
  [:> (.-div motion) props])

(js/console.log :foo!!)
