(ns pronto.core-test
  (:require [clojure.test :refer :all]
            [pronto.core :refer [defproto]])
  (:import [protogen.generated People$Person People$Person$Builder
            People$Address People$Address$Builder People$Like People$Level]))




(defn make-like [desc level]
  (-> (People$Like/newBuilder)
    (.setDesc desc)
    (.setLevel level)
    (.build)))

(defn make-address
  ([] (make-address "fooville" "broadway" 21213))
  ([city street house-num]
   (-> (People$Address/newBuilder)
     (.setCity city)
     (.setStreet street)
     (.setHouseNum house-num)
     (.build))))

(defn make-person
  ([] (make-person 5 "Foo"
                   "foo@bar.com"
                   (make-address)
                   [(make-like "low" People$Level/LOW)
                    (make-like "medium" People$Level/MEDIUM)
                    (make-like "high" People$Level/HIGH)]))
  ([id name email address likes]
   (-> (People$Person/newBuilder)
     (.setId id)
     (.setName name)
     (.setEmail email)
     (.setAddress address)
     (.addAllLikes likes)
     (.build))))

(defmacro ensure-immutable [w & body]
  `(do ~@(map (fn [expr]
             `(let [m1# (into {} ~w)
                    res# ~expr
                    m2# (into {} ~w)]
                (is (= m1# m2#)) 
               res#))
           body)))

(defn check-assoc [m k v]
  (ensure-immutable m
                    (is (= v (get (assoc m k v) k)))))

(defproto People$Person)
(deftest assoc-test
  
  (let [p (make-person)
        w (->wrapped-protogen-generated-People$Person p)]
    (check-assoc w :name "blachs")
    (check-assoc w :id (int 123)
                 )

    #_(ensure-immutable w
                      (is (= 239132 (:id (assoc w :id 239132))))
                      (is (= "blachs" (:name (assoc w :name "blachs"))))
                      (is (thrown? IllegalArgumentException
                                   (assoc w :fake-key 123)))

                      (is (thrown? IllegalArgumentException
                                   (assoc w :name 0.312)))

                      )
    ))


