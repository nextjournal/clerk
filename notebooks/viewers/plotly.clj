;; # Plotly 📈
(require '[nextjournal.viewer :as v])

(v/view-as :plotly {:data [{:y (shuffle (range 10)) :name "The Federation"}
                           {:y (shuffle (range 10)) :name "The Empire"}]})
