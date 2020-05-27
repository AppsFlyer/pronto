(ns pronto.core-test
  (:require [clojure.test :refer :all]
            [pronto.core :refer [defproto] :as p])
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
  [& {:keys [city street house-num house apartment]}]
  (cond-> (People$Address/newBuilder)
    city      (.setCity city)
    street    (.setStreet street)
    house-num (.setHouseNum house-num)
    house     (.setHouse house)
    apartment (.setApartment apartment)
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

(deftest map-test
  (let [city      "NYC"
        street    "Broadway"
        house-num 32
        num-rooms 3
        addr-map  {:city      city :street street
                   :house-num house-num
                   :house     {:num-rooms num-rooms}}
        addr      (map->People$AddressMap addr-map)
        p         (People$AddressMap->proto addr)]
    (is (= (.getCity p) (:city addr) city))
    (is (= (.getStreet p) (:street addr) street))
    (is (= (.getHouseNum p) (:house-num addr) house-num))
    (is (= (.getNumRooms (.getHouse p)) (get-in addr [:house :num-rooms])))

    (is (= (assoc addr-map
                  :apartment
                  (People$ApartmentMap->map (->People$ApartmentMap)))
           (People$AddressMap->map addr)))))

(deftest enum-test

  ;; test translations:

  (is (= :low (:level (proto->People$LikeMap (make-like :level People$Level/LOW)))))

  (is (= :medium (:level (proto->People$LikeMap (make-like :level People$Level/MEDIUM)))))

  (is (= :high (:level (proto->People$LikeMap (make-like :level People$Level/HIGH)))))

  ;; test transitions
  (let [p (make-like :desc "my description" :level People$Level/LOW)
        w (proto->People$LikeMap p)]
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
  (is (thrown? IllegalArgumentException (assoc (proto->People$PersonMap (make-person)) :fake-key 123))))

(deftest int-test
  (test-numeric int
                (proto->People$PersonMap (make-person))
                :id))

(deftest long-test
  (test-numeric long
                (proto->People$PersonMap (make-person))
                :age-millis))


(deftest double-test
  (test-numeric double
                (proto->People$PersonMap (make-person))
                :height-cm))

(deftest float-test
  (test-numeric float
                (proto->People$PersonMap (make-person))
                :weight-kg))


(deftest boolean-test
  (let [p (proto->People$PersonMap (make-person :is-vegetarian false))]
    (is (true? (check-assoc p :is-vegetarian true)))
    (is (thrown? IllegalArgumentException (assoc p :is-vegetarian nil)))
    (is (thrown? IllegalArgumentException (assoc p :is-vegetarian "1")))))


(deftest repeated-primitive-test
  (let [pet-names ["aaa" "bbb"]
        p         (make-person :pet-names pet-names)
        w         (proto->People$PersonMap p)]
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
        w     (proto->People$PersonMap p)]
    (is (= likes (:likes w)))
    (is (= [(make-like :desc "desc3" :level People$Level/HIGH)]
           (:likes (assoc w :likes [(make-like :desc "desc3" :level People$Level/HIGH)]))))
    (is (thrown? IllegalArgumentException (assoc w :likes 123)))
    (is (thrown? IllegalArgumentException (assoc w :likes [1 2 3])))
    (is (= [(make-like :desc "desc1" :level People$Level/HIGH)
            (make-like :desc "desc2" :level People$Level/HIGH)]
           (:likes (update w :likes (partial map (fn [x] (assoc x :level :high)))))))))

(deftest one-of-test
  (let [address   (map->People$AddressMap {})
        house     (make-house :num-rooms 5)
        apartment (make-apartment :floor-num 4)
        address2  (assoc address :house (proto->People$HouseMap house))
        address3  (assoc address2 :apartment (proto->People$ApartmentMap apartment))]
    (is (= (p/which-one-of address :home) :home-not-set))
    
    (is (= house (:house address2)))
    (is (= (p/which-one-of address2 :home) :house))

    (is (= apartment (:apartment address3)))
    (is (= (p/which-one-of address3 :home) :apartment))))

(deftest maps-test
  (let [bff    (make-person :name "bar")
        sister (make-person :name "baz")
        person (make-person :name "foo" :relations {"bff" bff})
        w      (proto->People$PersonMap person)]
    (is (= {:bff bff} (:relations (assoc-in w [:relations :bff] bff))))
    (is (= {:bff bff :sister sister} (:relations (assoc-in w [:relations :sister] sister))))))

(deftest empty-test
  (let [person (make-person :name "foo" :age 100)]
    (is (= (empty (proto->People$PersonMap person))
           (->People$PersonMap)))))



(deftest transient-test
  (let [transient-person (transient (proto->People$PersonMap (make-person)))]
    (assoc! transient-person :name "foo")
    (assoc! transient-person :id 2)
    (is (thrown? IllegalArgumentException (assoc! transient-person :fake-key "hello")))
    (is (thrown? IllegalArgumentException (assoc! transient-person :id "foo")))
    (is (= (persistent! transient-person) (proto->People$PersonMap (make-person :id 2 :name "foo"))))
    (is (thrown? IllegalAccessError (get transient-person :name)))))

(deftest bytes-test
  (let [person (make-person :id 5 :name "hello"
                            :address (make-address :city "some-city" :street "broadway")
                            :age-millis 111111)]
    (is (= person
           (People$PersonMap->proto (bytes->People$PersonMap (.toByteArray person)))))))

(deftest json-test
  (let [person (make-person :id 5 :name "hello"
                            :address (make-address :city "some-city" :street "broadway")
                            :age-millis 111111)]
    (is (= person
           (-> person
               proto->People$PersonMap
               People$PersonMap->json
               json->People$PersonMap)))))

(defn check-clear
  ([ctor field-name init-val default-val]
   (check-clear ctor field-name init-val default-val false))
  ([ctor field-name init-val default-val check-has?]
   (let [obj (ctor {field-name init-val})]
     (is (= init-val (get obj field-name)))
     (when check-has?
       (is (true? (p/has-field? obj field-name))))
     (is (= default-val (get (p/clear-field obj field-name) field-name)))
     (when check-has?
       (is (false? (p/has-field? (p/clear-field obj field-name) field-name)))))))


(deftest clear-field-test
  (check-clear map->People$PersonMap :id 5 0)

  (check-clear map->People$PersonMap :name "foo" "")

  (check-clear map->People$PersonMap :address
               (make-address :city "NYC" :street "Broadway")
               (make-address)
               true)

  (check-clear map->People$PersonMap :height-cm 5.0 0.0)

  (check-clear map->People$PersonMap :weight-kg 5.0 0.0)

  (check-clear map->People$PersonMap :is-vegetarian true false)

  (check-clear map->People$PersonMap :likes
               [(make-like :desc "wow" :level People$Level/LOW)]
               [])

  (check-clear map->People$PersonMap :relations
               {:friend (make-person)}
               {})

  (check-clear map->People$AddressMap :house
               (make-house :num-rooms 3)
               (make-house)
               true))


