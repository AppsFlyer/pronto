(ns pronto.compilation-test
  (:require [clojure.test :refer :all]
            [pronto.core :refer [defmapper]])
  (:import (protogen.generated Cycle$A)))

(deftest cyclic-schema-test
  (testing "defmapper should pass without errors"
    (eval '(pronto.core/defmapper
             cyclic-mapper
             [protogen.generated.Cycle$A]))))
