(ns pronto.core-test
  (:require [clojure.test :refer :all]
            [pronto.core :refer [defproto]])
  (:import [protogen.generated People$Person People$Person$Builder
            People$Address People$Address$Builder People$Like People$Level]))


(defn make-like [& {:keys [desc level]}]
  (cond-> (People$Like/newBuilder)
    desc (.setDesc desc)
    level (.setLevel level)
    true (.build)))

(defn make-address
  ([] (make-address :city "fooville" :street "broadway" :house-num 21213))
  ([& {:keys [city street house-num]}]
   (cond-> (People$Address/newBuilder)
     city (.setCity city)
     street (.setStreet street)
     house-num (.setHouseNum house-num)
     true (.build))))

(defn make-person
  ([] (make-person :id 5 :name "Foo"
                   :email "foo@bar.com"
                   :address (make-address)
                   :pet-names ["bla" "booga"]
                   :likes
                   [(make-like "low" People$Level/LOW)
                    (make-like "medium" People$Level/MEDIUM)
                    (make-like "high" People$Level/HIGH)]))
  ([& {:keys [id name email address likes pet-names]}]
   (cond-> (People$Person/newBuilder)
     id (.setId id)
     name (.setName name)
     email (.setEmail email)
     address (.setAddress address)
     likes (.addAllLikes likes)
     pet-names (.addAllPetNames pet-names)
     true (.build))))

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

(deftest enum-test

  ;; test translations:

  (is (= :low (:level (->wrapped-protogen-generated-People$Like (make-like :level People$Level/LOW)))))

  (is (= :medium (:level (->wrapped-protogen-generated-People$Like (make-like :level People$Level/MEDIUM)))))

  (is (= :high (:level (->wrapped-protogen-generated-People$Like (make-like :level People$Level/HIGH)))))

  ;; test transitions
  (let [p (make-like :desc "my description" :level People$Level/LOW)
        w (->wrapped-protogen-generated-People$Like p)]
    (is (= :low (:level w)))
    (is (= :medium (:level (assoc w :level :medium))))
    (is (= :high (:level (assoc w :level :high))))))

(deftest repeated-primitive
  (let [pet-names ["aaa" "bbb"]
        p (make-person :pet-names pet-names)
        w (->wrapped-protogen-generated-People$Person p)]
    (is (= pet-names (:pet-names w)))
    ))

#_(deftest assoc-test
  
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


