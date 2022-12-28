(ns pronto.tools-analyzer-test
  (:import (protogen.generated People$Person))
  (:require [clojure.tools.analyzer.jvm :as ana]
            [clojure.test :refer [deftest testing]]
            [pronto.core :as p]))

(deftest tools-analyzer-test
  (testing "tools.analyzer should successfully analyze generated code"
    ;; eval the form in order to avoid double class generation
    (eval
     '(do
        (pronto.core/defmapper mapper [protogen.generated.People$Person])
        (clojure.tools.analyzer.jvm/analyze
            '(pronto.core/defmapper mapper [protogen.generated.People$Person]))))))
