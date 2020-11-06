(ns pronto.reflection-test
  (:require [clojure.test :refer :all]
            [pronto.core :as p]
            [clj-java-decompiler.core :as d]))


(use-fixtures :each
  (fn [f]
    (p/unload-classes!)
    (f)))

(defn- reflects? [^String bytecode]
  (.contains bytecode "clojure/lang/Reflector"))

(defmacro reflection-free? [& body]
  `(let [^String bytecode# (with-out-str (d/disassemble (do ~@body)))]
     (is (not (reflects? bytecode#)))))


(deftest defproto-test
  (reflection-free? (pronto.core/defproto protogen.generated.People$Person)))


(deftest p->-test
  (reflection-free?
    (pronto.core/defproto protogen.generated.People$Person)
    (let [m (pronto.core/proto-map protogen.generated.People$Person)]
      (pronto.core/p-> m :id)
      (pronto.core/p-> m :address :city)
      (pronto.core/p->
        m
        (assoc :name "abc")
        (assoc :id 213)
        (assoc :address {:city "tel aviv"})
        (assoc-in [:address :street] "dizengof")
        (update :pet_names conj "booga")))))
