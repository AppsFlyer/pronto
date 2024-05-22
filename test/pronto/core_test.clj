(ns pronto.core-test
  (:require [clojure.test :refer :all]
            [pronto.core :refer [defmapper] :as p]
            [pronto.lens]
            [pronto.utils :as u]
            [clojure.string :as s]
            [clojure.walk])
  (:import [protogen.generated People$Person People$Person$Builder
            People$Address People$Like People$Level
            People$House People$Apartment
            People$UUID People$PersonOrBuilder]
           [com.google.protobuf ByteString]
           [clojure.lang ExceptionInfo]
           [pronto ProntoVector]))


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
  [& {:keys [id name email address likes pet-names relations private-key age-millis vegetarian? height-cm weight-kg levels ids-list relations-like-level]}]
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
    (when relations-like-level (.putAllRelationsLikeLevel b relations-like-level))
    (.build b)))

(defmacro ensure-immutable [w & body]
  `(do ~@(map (fn [expr]
                `(let [m1#  (into {} ~w)
                       res# ~expr
                       m2#  (into {} ~w)]
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

(defmapper mapper [People$Person]
  :encoders {protogen.generated.People$UUID
             {:from-proto #(try
                             (java.util.UUID/fromString (.getValue ^People$UUID %))
                             (catch Exception _))
              :to-proto   #(let [b (People$UUID/newBuilder)]
                             (.setValue b (str %))
                             (.build b))}})


(def empty-person (p/proto-map mapper People$Person))


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
        addr              (p/clj-map->proto-map mapper People$Address addr-map)
        ^People$Address a (p/proto-map->proto addr)]
    (is (= (.getCity a) (:city addr) city))
    (is (= (.getStreet a) (:street addr) street))
    (is (= (.getHouseNum a) (:house_num addr) house-num))
    (is (= (.getNumRooms ^People$House (.getHouse a)) (get-in addr [:house :num_rooms])))

    (is (= (assoc addr-map
                  :apartment
                  (p/proto-map->clj-map (p/proto-map mapper People$Apartment)))
           (p/proto-map->clj-map addr)))))

(deftest enum-test

  ;; test translations:

  (is (= :LOW (:level (p/proto->proto-map mapper (make-like :level People$Level/LOW)))))

  (is (= :MEDIUM (:level (p/proto->proto-map mapper (make-like :level People$Level/MEDIUM)))))

  (is (= :HIGH (:level (p/proto->proto-map mapper (make-like :level People$Level/HIGH)))))

  (is (= :HIGH (:level (p/proto->proto-map mapper (make-like :level People$Level/ALIASED_HIGH)))))

  ;; test transitions
  (let [p (make-like :desc "my description" :level People$Level/LOW)
        w (p/proto->proto-map mapper p)]
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
  (is (thrown? Exception (assoc (p/proto->proto-map mapper (make-person)) :fake-key 123))))

(deftest int-test
  (test-numeric int
                (p/proto->proto-map mapper (make-person))
                :id))

(deftest long-test
  (test-numeric long
                (p/proto->proto-map mapper (make-person))
                :age_millis))


(deftest double-test
  (test-numeric double
                (p/proto->proto-map mapper (make-person))
                :height_cm))

(deftest float-test
  (test-numeric float
                (p/proto->proto-map mapper (make-person))
                :weight_kg))


(deftest boolean-test
  (let [p (p/proto->proto-map  mapper (make-person :vegetarian? false))]
    (check-assoc p :is_vegetarian true)
    (check-assoc
      (p/proto->proto-map mapper (make-person :vegetarian? true))
      :is_vegetarian
      false)
    ;; TODO: how to test ex-info?
    (is (thrown? Exception (assoc p :is_vegetarian nil)))
    (is (thrown? Exception (assoc p :is_vegetarian "1")))))


(deftest repeated-string-test
  (let [pet-names ["aaa" "bbb"]
        p         (make-person :pet-names pet-names)
        w         (p/proto->proto-map mapper p)]
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
        w        (p/proto->proto-map mapper p)]
    (is (= ids-list (:ids_list w)))
    (is (= [3] (:ids_list (assoc w :ids_list [3]))))
    ;; TODO: how to test ex-info?
    (is (thrown? Exception (assoc w :ids_list 123)))
    (is (thrown? Exception (assoc w :ids_list ["1" "2" "3"])))))

(deftest repeated-message-test
  (let [likes [(make-like :desc "desc1" :level People$Level/LOW)
               (make-like :desc "desc2" :level People$Level/MEDIUM)]
        p     (make-person :likes likes)
        w     (p/proto->proto-map mapper p)]
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
        w      (p/proto->proto-map mapper p)]
    (is (= (:levels w) [:LOW :MEDIUM]))
    (is (thrown? Exception (assoc w :levels 123)))
    (is (thrown? Exception (assoc w :levels [1 2 3])))))

(deftest one-of-test
  (let [address   (p/clj-map->proto-map mapper People$Address {})
        house     (make-house :num_rooms 5)
        apartment (make-apartment :floor_num 4)
        address2  (assoc address :house (p/proto->proto-map mapper house))
        address3  (assoc address2 :apartment (p/proto->proto-map mapper apartment))]
    (is (nil? (p/which-one-of address :home)))
    (is (nil? (p/one-of address :home)))

    (is (= house (:house address2)))
    (is (= (p/which-one-of address2 :home) :house))
    (is (= house (p/one-of address2 :home)))

    (is (= apartment (:apartment address3)))
    (is (= (p/which-one-of address3 :home) :apartment))
    (is (= apartment (p/one-of address3 :home)))))

(deftest maps-test
  (let [bff    (make-person :name "bar")
        sister (make-person :name "baz")
        person (make-person :name "foo"
                            :relations {"bff" bff}
                            :relations-like-level {"bff" People$Level/HIGH})
        w      (p/proto->proto-map mapper person)]
    (is (= {"bff" bff} (:relations (assoc-in w [:relations "bff"] bff))))
    (is (= {"bff" bff "sister" sister} (:relations (assoc-in w [:relations "sister"] sister))))

    (is (= {"bff" :HIGH} (:relations_like_level w)))
    (is (= {"bff" :MEDIUM} (:relations_like_level (assoc-in w [:relations_like_level "bff"] :MEDIUM))))

    (is (= {"hello" "world"} (:s2s (assoc-in w [:s2s "hello"] "world"))))

    (is (thrown? ExceptionInfo (assoc w :relations nil)))
    (is (thrown? ExceptionInfo (assoc-in w [:relations "sister"] 1)))
    (is (thrown? ExceptionInfo (assoc-in w [:relations 123] "aaa")))
    (is (thrown? ExceptionInfo (assoc-in w [:s2s "a"] 1)))
    (is (thrown? ExceptionInfo (assoc-in w [:s2s 1] "a")))))

(deftest init-with-values
  (let [p (p/proto-map mapper
                        People$Person
                        :name "gaga"
                        :age_millis 11)]
    (is (= (:name p) "gaga"))
    (is (= (:age_millis p) 11))))

(deftest transient-test
  (let [transient-person (transient (p/proto->proto-map mapper (make-person)))]
    (assoc! transient-person :name "foo")
    (assoc! transient-person :id 2)
    (is (thrown? Exception (assoc! transient-person :fake-key "hello")))
    (is (thrown? Exception (assoc! transient-person :id "foo")))
    (is (= (persistent! transient-person) (p/proto->proto-map mapper (make-person :id 2 :name "foo"))))))

(deftest bytes-test
  (let [person (make-person :id 5 :name "hello"
                            :address (make-address :city "some-city" :street "broadway")
                            :age_millis 111111)]
    (is (= person
           (p/proto-map->proto (p/bytes->proto-map mapper People$Person (p/proto-map->bytes (p/proto->proto-map mapper person))))))))


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
  (check-clear #(p/clj-map->proto-map mapper People$Person %) :id 5 0)

  (check-clear #(p/clj-map->proto-map mapper People$Person %) :name "foo" "")

  (check-clear #(p/clj-map->proto-map mapper People$Person %) :address
               (make-address :city "NYC" :street "Broadway")
               nil
               true)

  (check-clear #(p/clj-map->proto-map mapper People$Person %) :height_cm 5.0 0.0)

  (check-clear #(p/clj-map->proto-map mapper People$Person %) :weight_kg 5.0 0.0)

  (check-clear #(p/clj-map->proto-map mapper People$Person %) :is_vegetarian true false)

  (check-clear #(p/clj-map->proto-map mapper People$Person %) :likes
               [(make-like :desc "wow" :level People$Level/LOW)]
               [])

  (check-clear #(p/clj-map->proto-map mapper People$Person %) :relations
               {"friend" (make-person)}
               {})

  (check-clear #(p/clj-map->proto-map mapper People$Address %) :house
               (make-house :num-rooms 3)
               nil
               true))


(deftest camel-case-test
  (is (= "IsS2S" (u/->camel-case "is_s2s")))
  (is (= "AfSub1" (u/->camel-case "af_sub1"))))

(deftest map-test
  (is (= (p/clj-map->proto-map mapper People$Person {})
         (p/clj-map->proto-map mapper People$Person nil))))


(deftest well-known-types-test
  (let [p (make-person)
        w (p/proto->proto-map mapper p)]
    (is (nil? (:maiden_name p)))
    (is (thrown? Exception (assoc w :maiden_name 123)))
    (is (= "Blabla" (:maiden_name (assoc w :maiden_name "Blabla"))))
    (is (nil? (:maiden_name (-> w (assoc :maiden_name "BlaBla") (assoc :maiden_name nil)))))))

(deftest custom-encoder-test
  (let [p    (make-person)
        w    (p/proto->proto-map mapper p)
        uuid (java.util.UUID/randomUUID)]
    (is (nil? (:uuid p)))
    (is (= uuid (:uuid (assoc w :uuid uuid))))
    (is (= (str uuid) (.getValue ^People$UUID (.getUuid ^People$Person (p/proto-map->proto (assoc w :uuid uuid))))))))

(deftest metadata-test
  (let [p  (make-person)
        w  (p/proto->proto-map mapper p)
        md {:x? true}]
    (is (nil? (meta w)))
    (is (= md (meta (with-meta w md))))
    (is (= md (meta (assoc (with-meta w md) :name "bla"))))
    (is (= md (meta (p/clj-map->proto-map mapper People$Person (with-meta {:name "bla"} md)))))))

(deftest proto-or-builder-impl-test
  (let [^People$PersonOrBuilder p (make-person :name "booga" :id 5 :levels [People$Level/MEDIUM])
        ^People$PersonOrBuilder w (p/proto->proto-map mapper p)]
    (is (= (.getName p) (.getName w)))
    (is (= (.getId p) (.getId w)))
    (is (= (.getLevelsList p) (.getLevelsList w)))))


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
  (is (true? (p/proto-map? (p/proto->proto-map mapper (make-person))))))


(deftest proto->proto-map-test []
  (let [address (make-address)]
    (is (identical? address (p/proto-map->proto (p/proto->proto-map mapper address))))))

(defn change-city [m city]
 (p/p-> m (assoc-in [:address :city] city)))

(deftest p->-test []
  (let [m     (p/proto-map mapper People$Person)
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
                  (assoc-in [:address :street] "Broadway")
                  (assoc-in [:address :house] {:num_rooms 5})
                  (assoc-in [:s2s "a"] "b")
                  (assoc-in [:relations "bff"] (p/proto-map mapper People$Person :name "bob"))
                  (update-in [:relations "bff" :name] s/capitalize)
                  (update-in [:relations "bff" :age_millis] inc)
                  (change-city "Boston"))
           (p/clj-map->proto-map
            mapper
            People$Person
            {:id          4
             :name        "Foo"
             :address     {:city      "Boston"
                           :street    "Broadway"
                           :house_num 6
                           :house     {:num_rooms 5}}
             :maiden_name "Booga"
             :s2s         {"a" "b"}
             :relations   {"bff" {:name "Bob" :age_millis 1}}
             :likes       [{:desc "desc1" :level :LOW}]}))
        (is (= m (p/proto-map mapper People$Person))))))


(deftest p->-test-hinted []
  (p/with-hints
    [(p/hint empty-person People$Person mapper)]
    (testing "it overrides"
      (is (= (p/p-> empty-person
                    (assoc :id 3)
                    (assoc :name "foo")
                    (assoc :id 4)
                    (update :id + 6)
                    (update :name s/upper-case)
                    (assoc-in [:likes] [(make-like :desc "desc1" :level People$Level/LOW)])
                    (update :maiden_name (constantly "Booga"))
                    (assoc-in [:s2s "a"] "b"))
             (p/clj-map->proto-map mapper People$Person {:name "FOO"
                                                         :id 10
                                                         :s2s {"a" "b"}
                                                         :likes [{:desc "desc1" :level :LOW}]
                                                         :maiden_name "Booga"}))))
    (testing "nested assoc-ins into message type"
      (is (= (p/p-> empty-person
                    (assoc-in [:address :city] "New York")
                    (assoc-in [:address :house_num] 3)
                    (update-in [:address :house_num] + 3)
                    (assoc-in [:address :street] "Broadway")
                    (assoc-in [:address :house] {:num_rooms 5}))
             (p/clj-map->proto-map mapper People$Person
                                   {:address {:city      "New York"
                                              :street    "Broadway"
                                              :house_num 6
                                              :house     {:num_rooms 5}}}))))))


(deftest p->-test-hinted2 []
  (let [person (assoc empty-person :name "Joe" :address {:city "New York"})]
    (p/with-hints
      [(p/hint person People$Person mapper)]
      (testing "associng into map types"
        (is (= (p/p-> person
                      (assoc-in [:relations "bff"] (assoc empty-person :name "bob"))
                      (update-in [:relations "bff" :name] s/capitalize)
                      (update-in [:relations "bff" :age_millis] inc))
               (p/clj-map->proto-map mapper People$Person
                                     {:name "Joe"
                                      :address {:city "New York"}
                                      :relations {"bff" {:name "Bob" :age_millis 1}}}))))
      (is (= (p/p-> person
                    :address
                    :city)
             "New York"))
      (is (= (p/p-> person (get-in [:address :city]))
             "New York"))
      (is (= (p/p-> person :name) "Joe"))
      (is (= (p/p-> person (get :name)) "Joe")))))


(deftest assoc-if-test []
  (let [person (assoc empty-person :name "Joe")]
    (is (= "Bob" (p/p-> person
                        (p/assoc-if :name "Bob")
                        (p/assoc-if :name (do nil))
                        :name)))))


(deftest clear-field-test []
  (let [person (assoc empty-person :address {:city "NYC"})]
    (is (nil? (p/p-> person (p/clear-field :address) :address)))))


(deftest pcond->-test []
  (let [m     (p/proto-map mapper People$Person)
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
           (p/clj-map->proto-map
             mapper
             People$Person
             {:id          3
              :name        "Foo"
              :address     {:city      "New York"
                            :street    ""
                            :house_num 3
                            :house     {:num_rooms 5}}
              :maiden_name "Booga"
              :likes       []}))
        (is (= m (p/proto-map mapper People$Person))))))

(deftest default-instance-test []
  (is (identical? (p/proto-map mapper People$Person) (p/proto-map mapper People$Person)))
  (is (identical? (p/proto-map mapper People$Person) (empty (assoc (p/proto-map mapper People$Person) :id 123)))))


(defmacro ensure-vector-immutable [w & body]
  `(do ~@(map (fn [expr]
                `(let [m1#  (into [] ~w)
                       res# ~expr
                       m2#  (into [] ~w)]
                   (is (= m1# m2#)) 
                   res#))
              body)))

(deftest pronto-vector-test []
  (let [likes [(make-like :desc "desc1" :level People$Level/LOW)
               (make-like :desc "desc2" :level People$Level/MEDIUM)
               (make-like :desc "desc3" :level People$Level/HIGH)]
        p     (p/proto-map mapper People$Person :likes likes)
        v     (:likes p)]
    (is (instance? ProntoVector v))
    (is (= likes v))
    (is (thrown? Exception (conj v 123)))
    (ensure-immutable
      v
      (is (= [] (empty v))))
    (ensure-immutable
      v
      (is (= (conj likes (make-like :desc "another like" :level People$Level/LOW))
             (conj v (make-like :desc "another like" :level People$Level/LOW)))))
    (ensure-immutable
      v
      (is (= (conj likes (make-like :desc "another like" :level People$Level/LOW))
             (conj v {:desc "another like" :level People$Level/LOW}))))
    (ensure-immutable
      v
      (is (= (assoc likes 0 (make-like "another like" People$Level/LOW))
             (assoc v 0 (make-like "another like" People$Level/LOW)))))
    (is (= (count v) 3))
    (ensure-immutable
      (is (= (count (conj v {}) 4))))
    (is (nil? (meta v)))
    (ensure-immutable
      v
      (let [m {:hello "world"}]
        (is (= (meta (with-meta v m)) m))))
    (ensure-immutable
      v
      (is (= (pop v) (pop likes))))))


(deftest proto-map->clj-map-test []
  (let [p        (p/proto-map mapper
                              People$Person
                              :likes [{}]
                              :address {})
        c        (p/proto-map->clj-map p)
        clj-map? #(and (map? %) (not (p/proto-map? %)))]
    (is (clj-map? c))
    (is (clj-map? (:address c)))
    (is (clj-map? (get-in c [:likes 0])))))



(deftest remove-default-values-xf-tests
  (testing "that default values are removed when converting to a clj-map using the xf"
    (is (= {:level :LOW}
           (-> (p/proto-map mapper People$Person)
               (p/proto-map->clj-map p/remove-default-values-xf)))))
  (testing "that default values are kept when converting to a clj-map"
    (is (= {:id                   0
            :name                 ""
            :email                ""
            :address              nil
            :likes                []
            :relations            {}
            :pet_names            []
            :private_key          ByteString/EMPTY
            :age_millis           0
            :is_vegetarian        false
            :height_cm            0.0
            :weight_kg            0.0
            :levels               []
            :social_security      nil
            :maiden_name          nil
            :uuid                 nil
            :bv                   nil
            :bla                  {}
            :ids_list             []
            :relations_like_level {}
            :num                  0
            :str                  ""
            :person               nil
            :level                :LOW
            :s2s                  {}
            :repeated_bytes       []
            :repeated_bools       []
            :repeated_doubles     []
            :repeated_floats      []}
           (-> (p/proto-map mapper People$Person)
               p/proto-map->clj-map)))))


(defmapper mapper-with-uuid-encoder [People$Person]
  :encoders
  {protogen.generated.People$UUID
   {:from-proto #(try
                   (java.util.UUID/fromString (.getValue ^People$UUID %))
                   (catch Exception _))
    :to-proto   #(let [b (People$UUID/newBuilder)]
                   (.setValue b (str %))
                   (.build b))}})

(defmapper mapper-without-uuid-encoder [People$Person])

(deftest mapper-interop-test []
  (let [person     (p/proto-map mapper-without-uuid-encoder People$Person)
        address-id (java.util.UUID/randomUUID)
        person     (assoc person
                          :address
                          (p/proto-map mapper-with-uuid-encoder
                                       People$Address
                                       :address_id
                                       address-id))]
    (testing "that address is now a protobuf UUID generated type rather than Java's UUID"
      (is (= (p/p-> person :address :address_id) {:value (str address-id)})))))

(deftest remap-test []
  (let [address-id (java.util.UUID/randomUUID)
        address    (p/proto-map mapper-with-uuid-encoder People$Address
                                :address_id address-id)]
    (testing "after remap, the address should have a protobuf UUID instance of address id"
      (is (= {:value (str address-id)}
             (:address_id (p/remap mapper-without-uuid-encoder address)))))

    (testing "remapping twice works and we're back to a Java UUID instance"
      (is (= address-id
             (:address_id (->> address
                               (p/remap mapper-without-uuid-encoder)
                               (p/remap mapper-with-uuid-encoder))))))))

(deftest free-vars-test []
  (let [address-class People$Address
        city          "tel aviv"
        street        "ibn gvirol"
        house-num     100
        address       (p/proto-map mapper People$Address
                                   :city city
                                   :street street
                                   :house_num house-num)]
    (is (= (p/proto-map mapper People$Address) (p/proto-map mapper address-class)))
    (is (= address (p/proto-map mapper address-class :city city :street street :house_num house-num)))
    (is (= address (p/bytes->proto-map mapper address-class (p/proto-map->bytes address))))
    (is (= (p/clj-map->proto-map mapper People$Address address)
           (p/clj-map->proto-map mapper address-class address)))))


(defn check-assoc-eagerness* [m k v p->?]
  (let [mi (gensym 'mi)
        ki (gensym 'ki)
        vi (gensym 'vi)
        mform `(do (swap! ~mi inc) ~m)
        kform `(do (swap! ~ki inc) ~k)
        vform `(do (swap! ~vi inc) ~v)]
    `(let [~mi (atom 0)
           ~ki (atom 0)
           ~vi (atom 0)]
       ~(if p->?
          `(p/p-> ~mform (assoc ~kform ~vform))
          `(assoc ~mform ~kform ~vform))
       (is (= @~mi 1))
       (is (= @~ki 1))
       (is (= @~vi 1)))))

(defmacro check-assoc-eagerness [m k v]
  `(do
     ~(check-assoc-eagerness* m k v false)
     ~(check-assoc-eagerness* m k v true)))

(deftest test-eagerness
  (testing "The value to be assoced must be eval'd once. More than once signifies
an error in the generated code"
    (let  [p (p/proto-map mapper People$Person)]
      (check-assoc-eagerness p :id 1)
      (check-assoc-eagerness p :name "hello")
      (check-assoc-eagerness p :address {:city "tel aviv"})
      (check-assoc-eagerness p :address (p/proto-map
                                        mapper
                                        People$Address
                                        :city "tel aviv"))
      (check-assoc-eagerness p :address
                           (p/proto-map->proto
                             (p/proto-map
                               mapper
                               People$Address
                               :city "tel aviv")))
      (check-assoc-eagerness p :likes [(p/proto-map
                                         mapper People$Like
                                         :level :HIGH)])
      (check-assoc-eagerness p :likes [{:level :HIGH}])
      (check-assoc-eagerness p :is_vegetarian true)
      (check-assoc-eagerness p :relations {"bff"
                                           {:name "booga"}})
      (check-assoc-eagerness p :relations {"bff"
                                           (p/proto-map
                                             mapper
                                             People$Person
                                             :name "booga")})
      (check-assoc-eagerness p :num 42))))


(deftest hash-test
  (testing "hashes of two equivalent proto maps should be equal"
    (is (= (hash (p/proto-map mapper People$Person))
           (hash (p/proto-map mapper People$Person))))
    (is (= (hash (p/proto-map mapper People$Person :name "joe"))
           (hash (p/proto-map mapper People$Person :name "joe"))))
    (is (= (hash (p/proto-map mapper People$Person :address {:city "NYC"}))
           (hash (p/proto-map mapper People$Person :address {:city "NYC"}))))))


(deftest test-end-to-end-proto-vector
  (testing "a proto vector of proto-maps should be able to be assoced back to a proto-map after some transformations"
    (let [p (p/proto-map mapper People$Person
                         :likes [(p/proto-map mapper People$Like :desc "desc1")
                                 {:desc "desc2"}
                                 (p/proto-map->proto (p/proto-map mapper People$Like :desc "desc3"))])
          ;; go through reduce
          new-likes (into [] (map #(update % :desc s/upper-case)) (:likes p))
          new-p (assoc p :likes new-likes)]
      (is (= ["DESC1" "DESC2" "DESC3"] (map :desc (:likes new-p)))))))


(deftest hint-validity
  (testing "hinting should only work within with-hints or p->"
    (is (thrown? Exception
                 (macroexpand '(pronto.lens/hint (p/proto-map mapper People$Person)
                                                 People$Person
                                                 mapper))))))

(deftest optional-field
  (testing "usage of optional fields"
    (let [p1 (p/proto-map mapper People$Like :level :LOW)
          p2 (p/proto-map mapper People$Like)]
      (is (= (:level p1) :LOW))
      (is (true? (p/has-field? p1 :level)))
      (-> (p/clear-field p1 :level)
          (p/has-field? :level)
          false?
          is)
      (is (= (p/clear-field p1 :level) p2))

      (-> (assoc p1 :level nil)
          (p/has-field? :level)
          false?
          is)
      (is (= (assoc p1 :level nil) p2))

      (is (false? (p/has-field? p2 :level)))
      (is (= (:level p2) nil))

      (is (= (assoc p2 :level :LOW) p1))
      (is (p/has-field? (assoc p2 :level :LOW) :level)))))
