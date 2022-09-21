;; # Analyzer issues with `clojure.core/proxy`
(ns proxy-issues)

(def test-proxy
  (proxy [clojure.lang.ISeq] []
    (seq [] '(this is a test seq))))

(into [] test-proxy)
