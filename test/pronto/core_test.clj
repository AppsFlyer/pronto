(ns pronto.core-test
  (:require [clojure.test :refer :all]
            [pronto.core :refer [defproto]])
  (:import [protogen.generated People$Person People$Person$Builder
            People$Address People$Address$Builder People$Like People$Level
            People$House People$Apartment]))

(defn make-house [& {:keys [num-rooms]}]
  (cond-> (People$House/newBuilder)
    num-rooms (.setNumRooms num-rooms)
    true (.build)))

(defn make-apartment [& {:keys [floor-num]}]
  (cond-> (People$Apartment/newBuilder)
    floor-num (.setFloorNum floor-num)
    true (.build)))

(defn make-like [& {:keys [desc level]}]
  (cond-> (People$Like/newBuilder)
    desc (.setDesc desc)
    level (.setLevel level)
    true (.build)))

(defn make-address
  [& {:keys [city street house-num]}]
  (cond-> (People$Address/newBuilder)
    city (.setCity city)
    street (.setStreet street)
    house-num (.setHouseNum house-num)
    true (.build)))

(defn make-person
  [& {:keys [id name email address likes pet-names relations private-key]}]
  (cond-> (People$Person/newBuilder)
    id (.setId id)
    name (.setName name)
    email (.setEmail email)
    address (.setAddress address)
    likes (.addAllLikes likes)
    pet-names (.addAllPetNames pet-names)
    relations (.putAllRelations relations)
    private-key (.setPrivateKey private-key)
    true (.build)))

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
    (is (= ["ccc"] (:pet-names (assoc w :pet-names ["ccc"]))))
    (is (thrown? IllegalArgumentException (assoc w :pet-names 123)))
    (is (thrown? IllegalArgumentException (assoc w :pet-names [1 2 3])))
    (is (= ["AAA" "BBB"] (:pet-names (update w :pet-names (partial map clojure.string/upper-case)))))
    (is (= ["hello" "aaa" "bbb"] (:pet-names (update w :pet-names (partial cons "hello")))))))

(deftest repeated-message
  (let [likes [(make-like :desc "desc1" :level People$Level/LOW)
               (make-like :desc "desc2" :level People$Level/MEDIUM)]
        p     (make-person :likes likes)
        w     (->wrapped-protogen-generated-People$Person p)]
    ;; TODO: fix equality
    #_(is (= likes (:likes w)))
    #_(is (= [(make-like :desc "desc3" :level People$Level/HIGH)] (:likes (assoc w :likes [(make-like :desc "desc3" :level People$Level/HIGH)]))))
    (is (thrown? IllegalArgumentException (assoc w :likes 123)))
    (is (thrown? IllegalArgumentException (assoc w :likes [1 2 3])))

    ;; TODO: fix equality

    #_(is (= [(make-like :desc "desc1" :level People$Level/HIGH)
            (make-like :desc "desc2" :level People$Level/HIGH)] (:likes (update w :likes (partial map (fn [x] (assoc x :level :high)))))))))

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


