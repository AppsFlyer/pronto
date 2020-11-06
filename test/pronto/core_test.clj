(ns pronto.core-test
  (:require [clojure.test :refer :all]
            [pronto.core :refer [defproto] :as p]
            [pronto.utils :as u]
            [pronto.emitters :as e]
            [clj-java-decompiler.core :as d])
  (:import [protogen.generated People$Person People$Person$Builder
            People$Address People$Address$Builder People$Like People$Level
            People$House People$Apartment
            People$UUID People$PersonOrBuilder]
           [com.google.protobuf ByteString]))


;; TODO: break this file into multiple ns's


(defn make-house [& {:keys [num-rooms]}]
  (let [b (People$House/newBuilder)]
    (when num-rooms (.setNumRooms b num-rooms))
    (.build b)))

(defn make-apartment [& {:keys [floor-num]}]
  (let [b (People$Apartment/newBuilder)]
    (when floor-num (.setFloorNum b floor-num))
    (.build b)))

(defn make-like [& {:keys [desc level]}]
  (let [b (People$Like/newBuilder)]
    (when desc  (.setDesc b desc))
    (when level (.setLevel b level))
    (.build b)))

(defn make-address
  [& {:keys [city street house-num house apartment]}]
  (let [b (People$Address/newBuilder)]
    (when city      (.setCity b city))
    (when street    (.setStreet b street))
    (when house-num (.setHouseNum b house-num))
    (when house     (.setHouse b ^People$House house))
    (when apartment (.setApartment b ^People$Apartment apartment))
    (.build b)))

(defn make-person
  [& {:keys [id name email address likes pet-names relations private-key age-millis vegetarian? height-cm weight-kg levels ids-list]}]
  (let [^People$Person$Builder b (People$Person/newBuilder)]
    (when id (.setId b id))
    (when name (.setName b name))
    (when email (.setEmail b email))
    (when address (.setAddress b ^People$Address address))
    (when likes   (.addAllLikes b likes))
    (when pet-names (.addAllPetNames b pet-names))
    (when relations   (.putAllRelations b relations))
    (when private-key (.setPrivateKey b private-key))
    (when age-millis  (.setAgeMillis b age-millis))
    (when vegetarian? (.setIsVegetarian b vegetarian?))
    (when height-cm   (.setHeightCm b height-cm))
    (when weight-kg   (.setWeightKg b weight-kg))
    (when levels      (.addAllLevels b levels))
    (when ids-list (.addAllIdsList b ids-list))
    (.build b)))

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
                           curr-val (get new-m k)]
                       (is (= expected-result curr-val))
                       (is (= (type expected-result) (type curr-val)))))))

(defproto People$Person
  :encoders {protogen.generated.People$UUID
             {:from-proto #(try
                             (java.util.UUID/fromString (.getValue ^People$UUID %))
                             (catch Exception _))
              :to-proto   #(let [b (People$UUID/newBuilder)]
                             (.setValue b (str %))
                             (.build b))}})


(deftest dependencies-test
  (is (= #{People$Address
           People$House
           People$Apartment
           People$Like
           com.google.protobuf.Int32Value
           com.google.protobuf.StringValue
           protogen.generated.People$UUID
           com.google.protobuf.BytesValue
           com.google.protobuf.DoubleValue}
         (p/dependencies People$Person)))
  (is (true? (p/depends-on? People$Person People$Address)))
  (is (false? (p/depends-on? People$Address People$Person))))

(deftest map-test
  (let [city              "NYC"
        street            "Broadway"
        house-num         32
        num-rooms         3
        addr-map          {:city      city :street street
                           :house_num house-num
                           :house     {:num_rooms num-rooms}}
        addr              (p/clj-map->proto-map People$Address addr-map)
        ^People$Address a (p/proto-map->proto addr)]
    (is (= (.getCity a) (:city addr) city))
    (is (= (.getStreet a) (:street addr) street))
    (is (= (.getHouseNum a) (:house_num addr) house-num))
    (is (= (.getNumRooms ^People$House (.getHouse a)) (get-in addr [:house :num_rooms])))

    (is (= (assoc addr-map
                  :apartment
                  (p/proto-map->clj-map (p/proto-map People$Apartment)))
           (p/proto-map->clj-map addr)))))

(deftest enum-test

  ;; test translations:

  (is (= :LOW (:level (p/proto->proto-map (make-like :level People$Level/LOW)))))

  (is (= :MEDIUM (:level (p/proto->proto-map (make-like :level People$Level/MEDIUM)))))

  (is (= :HIGH (:level (p/proto->proto-map (make-like :level People$Level/HIGH)))))

  (is (= :HIGH (:level (p/proto->proto-map (make-like :level People$Level/ALIASED_HIGH)))))

  ;; test transitions
  (let [p (make-like :desc "my description" :level People$Level/LOW)
        w (p/proto->proto-map p)]
    (is (= :LOW (:level w)))
    (is (= :MEDIUM (:level (assoc w :level :MEDIUM))))
    (is (= :HIGH (:level (assoc w :level :HIGH))))
    (is (= :HIGH (:level (assoc w :level :ALIASED_HIGH))))))


(defn test-numeric [type-ctor m k]
  (check-assoc m k (long 5) (type-ctor 5))
  (check-assoc m k (int 5) (type-ctor 5))
  (check-assoc m k (short 5) (type-ctor 5))
  (check-assoc m k (byte 5) (type-ctor 5))
  (check-assoc m k (double 5) (type-ctor 5))
  (check-assoc m k (float 5) (type-ctor 5))
  (check-assoc m k (clojure.lang.Numbers/toRatio 5) (type-ctor 5))
  (check-assoc m k 5N (type-ctor 5))
  ;; TODO: how to test ex-info?
  (is (thrown? Exception (assoc m k nil)))
  (is (thrown? Exception (assoc m k "1"))))

(deftest bad-assoc
  ;; TODO: how to test ex-info?
  (is (thrown? Exception (assoc (p/proto->proto-map (make-person)) :fake-key 123))))

(deftest int-test
  (test-numeric int
                (p/proto->proto-map (make-person))
                :id))

(deftest long-test
  (test-numeric long
                (p/proto->proto-map (make-person))
                :age_millis))


(deftest double-test
  (test-numeric double
                (p/proto->proto-map (make-person))
                :height_cm))

(deftest float-test
  (test-numeric float
                (p/proto->proto-map (make-person))
                :weight_kg))


(deftest boolean-test
  (let [p (p/proto->proto-map (make-person :is_vegetarian false))]
    (is (true? (check-assoc p :is_vegetarian true)))
    ;; TODO: how to test ex-info?
    (is (thrown? Exception (assoc p :is_vegetarian nil)))
    (is (thrown? Exception (assoc p :is_vegetarian "1")))))


(deftest repeated-string-test
  (let [pet-names ["aaa" "bbb"]
        p         (make-person :pet-names pet-names)
        w         (p/proto->proto-map p)]
    (is (= pet-names (:pet_names w)))
    (is (= ["ccc"] (:pet_names (assoc w :pet_names ["ccc"]))))
    ;; TODO: how to test ex-info?
    (is (thrown? Exception (assoc w :pet_names 123)))
    (is (thrown? Exception (assoc w :pet_names [1 2 3])))
    (is (= ["AAA" "BBB"] (:pet_names (update w :pet_names (partial map clojure.string/upper-case)))))
    (is (= ["hello" "aaa" "bbb"] (:pet_names (update w :pet_names (partial cons "hello")))))))

(deftest repeated-primitive-test
  (let [ids-list [(int 1) (int 2)]
        p        (make-person :ids-list ids-list)
        w        (p/proto->proto-map p)]
    (is (= ids-list (:ids_list w)))
    (is (= [3] (:ids_list (assoc w :ids_list [3]))))
    ;; TODO: how to test ex-info?
    (is (thrown? Exception (assoc w :ids_list 123)))
    (is (thrown? Exception (assoc w :ids_list ["1" "2" "3"])))))

(deftest repeated-message-test
  (let [likes [(make-like :desc "desc1" :level People$Level/LOW)
               (make-like :desc "desc2" :level People$Level/MEDIUM)]
        p     (make-person :likes likes)
        w     (p/proto->proto-map p)]
    (is (= likes (:likes w)))
    (is (= [(make-like :desc "desc3" :level People$Level/HIGH)]
           (:likes (assoc w :likes [(make-like :desc "desc3" :level People$Level/HIGH)]))))
    ;; TODO: how to test ex-info?
    (is (thrown? Exception (assoc w :likes 123)))
    (is (thrown? Exception (assoc w :likes [1 2 3])))
    (is (= [(make-like :desc "desc1" :level People$Level/HIGH)
            (make-like :desc "desc2" :level People$Level/HIGH)]
           (:likes (update w :likes (partial map (fn [x] (assoc x :level :HIGH)))))))))

(deftest repeated-enum-test
  (let [levels [People$Level/LOW
                People$Level/MEDIUM]
        p      (make-person :levels levels)
        w      (p/proto->proto-map p)]
    (is (= (:levels w) [:LOW :MEDIUM]))
    (is (thrown? Exception (assoc w :levels 123)))
    (is (thrown? Exception (assoc w :levels [1 2 3])))))

(deftest one-of-test
  (let [address   (p/clj-map->proto-map People$Address {})
        house     (make-house :num_rooms 5)
        apartment (make-apartment :floor_num 4)
        address2  (assoc address :house (p/proto->proto-map house))
        address3  (assoc address2 :apartment (p/proto->proto-map apartment))]
    (is (nil? (p/which-one-of address :home)))
    (is (nil? (p/one-of address :home)))

    (is (= house (:house address2)))
    (is (= (p/which-one-of address2 :home) :house))
    (is (= house (p/one-of address2 :home)))

    (is (= apartment (:apartment address3)))
    (is (= (p/which-one-of address3 :home) :apartment))
    (is (= apartment (p/one-of address3 :home)))))

(deftest maps-test
  (let [bff (make-person :name "bar")
        sister (make-person :name "baz")
        person (make-person :name "foo" :relations {"bff" bff})
        w (p/proto->proto-map person)]
    (is (= {:bff bff} (:relations (assoc-in w [:relations :bff] bff))))
    (is (= {:bff bff :sister sister} (:relations (assoc-in w [:relations :sister] sister))))))

(deftest init-with-values
  (let [p (p/proto-map People$Person
                       :name "gaga"
                       :age_millis 11)]
    (is (= (:name p) "gaga"))
    (is (= (:age_millis p) 11))))

(deftest transient-test
  (let [transient-person (transient (p/proto->proto-map (make-person)))]
    (assoc! transient-person :name "foo")
    (assoc! transient-person :id 2)
    (is (thrown? Exception (assoc! transient-person :fake-key "hello")))
    (is (thrown? Exception (assoc! transient-person :id "foo")))
    (is (= (persistent! transient-person) (p/proto->proto-map (make-person :id 2 :name "foo"))))))

(deftest bytes-test
  (let [person (make-person :id 5 :name "hello"
                            :address (make-address :city "some-city" :street "broadway")
                            :age_millis 111111)]
    (is (= person
           (p/proto-map->proto (p/bytes->proto-map People$Person (p/proto-map->bytes (p/proto->proto-map person))))))))


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
  (check-clear #(p/clj-map->proto-map People$Person %) :id 5 0)

  (check-clear #(p/clj-map->proto-map People$Person %) :name "foo" "")

  (check-clear #(p/clj-map->proto-map People$Person %) :address
               (make-address :city "NYC" :street "Broadway")
               nil
               true)

  (check-clear #(p/clj-map->proto-map People$Person %) :height_cm 5.0 0.0)

  (check-clear #(p/clj-map->proto-map People$Person %) :weight_kg 5.0 0.0)

  (check-clear #(p/clj-map->proto-map People$Person %) :is_vegetarian true false)

  (check-clear #(p/clj-map->proto-map People$Person %) :likes
               [(make-like :desc "wow" :level People$Level/LOW)]
               [])

  (check-clear #(p/clj-map->proto-map People$Person %) :relations
               {:friend (make-person)}
               {})

  (check-clear #(p/clj-map->proto-map People$Address %) :house
               (make-house :num-rooms 3)
               nil
               true))


(deftest camel-case-test
  (is (= "IsS2S" (u/->camel-case "is_s2s")))
  (is (= "AfSub1" (u/->camel-case "af_sub1"))))

(deftest map-test
  (is (= (p/clj-map->proto-map People$Person {}) (p/clj-map->proto-map People$Person nil))))


(deftest well-known-types-test
  (let [p (make-person)
        w (p/proto->proto-map p)]
    (is (nil? (:maiden_name p)))
    (is (thrown? Exception (assoc w :maiden_name 123)))
    (is (= "Blabla" (:maiden_name (assoc w :maiden_name "Blabla"))))
    (is (nil? (:maiden_name (-> w (assoc :maiden_name "BlaBla") (assoc :maiden_name nil)))))))

(deftest custom-encoder-test
  (let [p    (make-person)
        w    (p/proto->proto-map p)
        uuid (java.util.UUID/randomUUID)]
    (is (nil? (:uuid p)))
    (is (= uuid (:uuid (assoc w :uuid uuid))))
    (is (= (str uuid) (.getValue ^People$UUID (.getUuid ^People$Person (p/proto-map->proto (assoc w :uuid uuid))))))))

(deftest metadata-test
  (let [p  (make-person)
        w  (p/proto->proto-map p)
        md {:x? true}]
    (is (nil? (meta w)))
    (is (= md (meta (with-meta w md))))
    (is (= md (meta (assoc (with-meta w md) :name "bla"))))
    (is (= md (meta (p/clj-map->proto-map People$Person (with-meta {:name "bla"} md)))))))

(deftest proto-or-builder-impl-test
  (let [^People$PersonOrBuilder p (make-person :name "booga" :id 5 :levels [People$Level/MEDIUM])
        ^People$PersonOrBuilder w (p/proto->proto-map p)]
    (is (= (.getName p) (.getName w)))
    (is (= (.getId p) (.getId w)))
    (is (= (.getLevelsList p) (.getLevelsList w)))))

#_(deftest inflate-test
    (let [^People$Address address (make-address :city "NYC" :street "Broadway")
          p                       (-> (p/proto-map People$Person)
                                      (assoc :name "Name"))]
      (is (nil? (:address p)))
      (is (= (p/proto-map->proto (p/proto-map People$Address)) (p/proto-map->proto (:address (p/inflate p)))))
      (is (= "Name" (:name (p/inflate p))))
      (is (nil? (:address (p/deflate (p/inflate p)))))
      (is (= address (p/proto-map->proto (:address (assoc (p/inflate p) :address address)))))))


(deftest implode-test
  (is (= [] (u/implode [])))
  (is (= [1] (u/implode [1])))
  (is (= [1 [2 [3 [4]]]] (u/implode [1 2 3 4]))))



(deftest kv-forest-test []
  (is (= [] (u/kv-forest [])))
  (is (= [[:a [{:val 1}]]]
         (u/kv-forest [[[:a] 1]])))
  (is (= [[:a [{:val 1}
               {:val 2}]]]
         (u/kv-forest [[[:a] 1]
                       [[:a] 2]])))
  (is (= [[:a [{:val 1}]] [:b [{:val 2}]]]
         (u/kv-forest [[[:a] 1]
                       [[:b] 2]])))
  (is (= [[:a
           [[:b [[:c [{:val 1}]]
                 [:d [{:val 2}]]]]
            [:e [[:f [{:val 3}]]
                 [:g [{:val 4} {:val 5}]]]]]]]
         (u/kv-forest [[[:a :b :c] 1]
                       [[:a :b :d] 2]
                       [[:a :e :f] 3]
                       [[:a :e :g] 4]
                       [[:a :e :g] 5]]))))


(defn kvs->tree->kvs [kvs]
  (is (= kvs (u/flatten-forest (u/kv-forest kvs)))))

(deftest flatten-forest-test []
  (kvs->tree->kvs [])
  (kvs->tree->kvs [[[:a] 1]])
  (kvs->tree->kvs [[[:a :b :c] 1]
                   [[:a :b :d] 2]
                   [[:a :e :f] 3]
                   [[:a :e :g] 4]
                   [[:a :b :h] 5]
                   [[:a :e :i] 6]]))


(deftest proto-map?-test []
  (is (false? (p/proto-map? 1)))
  (is (false? (p/proto-map? (make-person))))
  (is (true? (p/proto-map? (p/proto->proto-map (make-person))))))

(defn change-city [^:transient-proto m city]
  (p/p-> m (assoc-in [:address :city] city)))

(deftest p->-test []
  (let [m     (p/proto-map People$Person)
        a-key :name]
    (is (= (p/p-> m
                  (assoc-in [:address :city] "New York")
                  :address
                  :city)
           "New York"))
    (is (= (p/p-> m
                  (assoc :id 3)
                  (assoc :id 4)
                  (assoc-in [:address :city] "New York")
                  (assoc-in [:address :house_num] 3)
                  (update :maiden_name (constantly "Booga"))
                  (assoc-in [:likes] [(make-like :desc "desc1" :level People$Level/LOW)])
                  (update-in [:address :house_num] + 3)
                  (assoc a-key "Foo")
                  (change-city "Boston")
                  (assoc-in [:address :street] "Broadway")
                  (assoc-in [:address :house] {:num_rooms 5}))
           (p/clj-map->proto-map People$Person
                                 {:id          4
                                  :name        "Foo"
                                  :address     {:city      "Boston"
                                                :street    "Broadway"
                                                :house_num 6
                                                :house     {:num_rooms 5}}
                                  :maiden_name "Booga"
                                  :likes       [{:desc "desc1" :level :LOW}]}))
        (is (= m (p/proto-map People$Person))))))

(deftest pcond->-test []
  (let [m     (p/proto-map People$Person)
        a-key :name]
    (is (= (p/pcond-> m
                      true (assoc :id 3)
                      true (assoc-in [:address :city] "New York")
                      false (assoc-in [:address :house_num] 3)
                      true (update :maiden_name (constantly "Booga"))
                      false (assoc-in [:likes] [(make-like :desc "desc1" :level People$Level/LOW)])
                      true (update-in [:address :house_num] + 3)
                      true (assoc a-key "Foo")
                      false (change-city "Boston")
                      false (assoc-in [:address :street] "Broadway")
                      true (assoc-in [:address :house] {:num_rooms 5}))
           (p/clj-map->proto-map People$Person
                                 {:id          3
                                  :name        "Foo"
                                  :address     {:city      "New York"
                                                :street    ""
                                                :house_num 3
                                                :house     {:num_rooms 5}}
                                  :maiden_name "Booga"
                                  :likes       []}))
        (is (= m (p/proto-map People$Person))))))

(deftest default-instance-test []
  (is (identical? (p/proto-map People$Person) (p/proto-map People$Person)))
  (is (identical? (p/proto-map People$Person) (empty (assoc (p/proto-map People$Person) :id 123)))))
