(ns pronto.core-test
  (:require [clojure.test :refer :all]
            [pronto.core :refer [defproto] :as p]
            [pronto.proto :as proto])
  (:import [protogen.generated People$Person People$Person$Builder
            People$Address People$Address$Builder People$Like People$Level
            People$House People$Apartment]
           [com.google.protobuf ByteString]))

(defn make-house [& {:keys [num-rooms]}]
  (cond-> (People$House/newBuilder)
    num-rooms (.setNumRooms num-rooms)
    true      (.build)))

(defn make-apartment [& {:keys [floor-num]}]
  (cond-> (People$Apartment/newBuilder)
    floor-num (.setFloorNum floor-num)
    true      (.build)))

(defn make-like [& {:keys [desc level]}]
  (cond-> (People$Like/newBuilder)
    desc  (.setDesc desc)
    level (.setLevel level)
    true  (.build)))

(defn make-address
  [& {:keys [city street house-num]}]
  (cond-> (People$Address/newBuilder)
    city      (.setCity city)
    street    (.setStreet street)
    house-num (.setHouseNum house-num)
    true      (.build)))

(defn make-person
  [& {:keys [id name email address likes pet-names relations private-key age-millis vegetarian? height-cm weight-kg]}]
  (cond-> (People$Person/newBuilder)
    id          (.setId id)
    name        (.setName name)
    email       (.setEmail email)
    address     (.setAddress address)
    likes       (.addAllLikes likes)
    pet-names   (.addAllPetNames pet-names)
    relations   (.putAllRelations relations)
    private-key (.setPrivateKey private-key)
    age-millis  (.setAgeMillis age-millis)
    vegetarian? (.setIsVegetarian vegetarian?)
    height-cm   (.setHeightCm height-cm)
    weight-kg   (.setWeightKg weight-kg)
    true        (.build)))

(defmacro ensure-immutable [w & body]
  `(do ~@(map (fn [expr]
                `(let [m1# (into {} ~w)
                       res# ~expr
                       m2# (into {} ~w)]
                   (is (= m1# m2#)) 
                   res#))
              body)))

(defn check-assoc
  ([m k v]
   (check-assoc m k v v))
  ([m k v expected-result]
   (ensure-immutable m
                     (let [new-m    (assoc m k v)
                           curr-val (get (assoc m k v) k)]
                       (is (= expected-result curr-val))
                       (is (= (type expected-result) (type curr-val)))))))


(defproto People$Person)


(deftest resolve-deps-test
  (is (= [People$Address
          People$House
          People$Apartment
          People$Like]
         (p/resolve-deps People$Person))))

(deftest map->wrapper-test
  (let [city      "NYC"
        street    "Broadway"
        house-num 32
        num-rooms 3
        addr-map  {:city      city      :street street
                   :house-num house-num :house  {:num-rooms num-rooms}}
        addr      (proto->protogen-generated-People$Address addr-map)
        p         (p/get-proto addr)]
    (is (= (.getCity p) (:city addr) city))
    (is (= (.getStreet p) (:street addr) street))
    (is (= (.getHouseNum p) (:house-num addr) house-num))
    (is (= (.getNumRooms (.getHouse p)) (get-in addr [:house :num-rooms])))))

(deftest message-test
  (let [p (make-person)
        w (proto->protogen-generated-People$Person p)
        ]
    ))

(deftest enum-test

  ;; test translations:

  (is (= :low (:level (proto->protogen-generated-People$Like (make-like :level People$Level/LOW)))))

  (is (= :medium (:level (proto->protogen-generated-People$Like (make-like :level People$Level/MEDIUM)))))

  (is (= :high (:level (proto->protogen-generated-People$Like (make-like :level People$Level/HIGH)))))

  ;; test transitions
  (let [p (make-like :desc "my description" :level People$Level/LOW)
        w (proto->protogen-generated-People$Like p)]
    (is (= :low (:level w)))
    (is (= :medium (:level (assoc w :level :medium))))
    (is (= :high (:level (assoc w :level :high))))))


(defn test-numeric [type-ctor m k]
  (check-assoc m k (long 5) (type-ctor 5))
  (check-assoc m k (int 5) (type-ctor 5))
  (check-assoc m k (short 5) (type-ctor 5))
  (check-assoc m k (byte 5) (type-ctor 5))
  (check-assoc m k (double 5) (type-ctor 5))
  (check-assoc m k (float 5) (type-ctor 5))
  (check-assoc m k (clojure.lang.Numbers/toRatio 5) (type-ctor 5))
  (check-assoc m k 5N (type-ctor 5))
  (is (thrown? IllegalArgumentException (assoc m k nil)))
  (is (thrown? IllegalArgumentException (assoc m k "1"))))

(deftest bad-assoc
  (is (thrown? IllegalArgumentException (assoc (proto->protogen-generated-People$Person (make-person)) :fake-key 123))))

(deftest int-test
  (test-numeric int
                (proto->protogen-generated-People$Person (make-person))
                :id))

(deftest long-test
  (test-numeric long
                (proto->protogen-generated-People$Person (make-person))
                :age-millis))


(deftest double-test
  (test-numeric double
                (proto->protogen-generated-People$Person (make-person))
                :height-cm))

(deftest float-test
  (test-numeric float
                (proto->protogen-generated-People$Person (make-person))
                :weight-kg))


(deftest boolean-test
  (let [p (proto->protogen-generated-People$Person (make-person :is-vegetarian false))]
    (is (true? (check-assoc p :is-vegetarian true)))
    (is (thrown? IllegalArgumentException (assoc p :is-vegetarian nil)))
    (is (thrown? IllegalArgumentException (assoc p :is-vegetarian "1")))))


(deftest repeated-primitive-test
  (let [pet-names ["aaa" "bbb"]
        p         (make-person :pet-names pet-names)
        w         (proto->protogen-generated-People$Person p)]
    (is (= pet-names (:pet-names w)))
    (is (= ["ccc"] (:pet-names (assoc w :pet-names ["ccc"]))))
    (is (thrown? IllegalArgumentException (assoc w :pet-names 123)))
    (is (thrown? IllegalArgumentException (assoc w :pet-names [1 2 3])))
    (is (= ["AAA" "BBB"] (:pet-names (update w :pet-names (partial map clojure.string/upper-case)))))
    (is (= ["hello" "aaa" "bbb"] (:pet-names (update w :pet-names (partial cons "hello")))))))

(deftest repeated-message-test
  (let [likes [(make-like :desc "desc1" :level People$Level/LOW)
               (make-like :desc "desc2" :level People$Level/MEDIUM)]
        p     (make-person :likes likes)
        w     (proto->protogen-generated-People$Person p)]
    (is (= likes (:likes w)))
    (is (= [(make-like :desc "desc3" :level People$Level/HIGH)]
           (:likes (assoc w :likes [(make-like :desc "desc3" :level People$Level/HIGH)]))))
    (is (thrown? IllegalArgumentException (assoc w :likes 123)))
    (is (thrown? IllegalArgumentException (assoc w :likes [1 2 3])))
    (is (= [(make-like :desc "desc1" :level People$Level/HIGH)
            (make-like :desc "desc2" :level People$Level/HIGH)]
           (:likes (update w :likes (partial map (fn [x] (assoc x :level :high)))))))))

(deftest one-of-test
  (let [address   (proto->protogen-generated-People$Address {})
        house     (make-house :num-rooms 5)
        apartment (make-apartment :floor-num 4)
        address2  (assoc address :house (proto->protogen-generated-People$House house))
        address3  (assoc address2 :apartment (proto->protogen-generated-People$Apartment apartment))]
    (is (nil? (:house address)))
    (is (nil? (:apartment address)))

    (is (= house (:house address2)))
    (is (nil? (:apartment address2)))

    (is (= apartment (:apartment address3)))
    (is (nil? (:house address3)))))

(deftest maps-test
  (let [bff    (make-person :name "bar")
        sister (make-person :name "baz")
        person (make-person :name "foo" :relations {"bff" bff})
        w      (proto->protogen-generated-People$Person person)]
    (is (= {:bff bff} (:relations (assoc-in w [:relations :bff] bff))))
    (is (= {:bff bff :sister sister} (:relations (assoc-in w [:relations :sister] sister))))))

(deftest empty-test
  (let [person       (make-person :name "foo" :age 100)
        empty-person (make-person)]
    (is (= (empty (proto->protogen-generated-People$Person person))
           (proto->protogen-generated-People$Person empty-person)))))


