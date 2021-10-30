(ns pronto.schema-test
  (:require [clojure.test :refer :all]
            [pronto.core :as p]
            [pronto.schema :as scm])
  (:import [com.google.protobuf ByteString]
           [protogen.generated People$Person
            People$Address People$Like
            People$House People$Apartment
            People$UUID]))

(p/defmapper mapper [People$Person])


(deftest schema-test []
  (is (= (scm/schema (p/proto-map mapper People$Person))
         (scm/schema People$Person)))

  (is (nil? (scm/schema People$Person :fake-key)))

  (is (= Integer/TYPE (scm/schema People$Person :id)))

  (is (= String (scm/schema People$Person :address :city)))

  (is (thrown? Exception (scm/schema People$Person :id :_)))
  
  (is (= (scm/schema People$Person :address)
         {:city           String
          :street         String
          :house_num      Integer/TYPE
          :home/house     People$House
          :home/apartment People$Apartment
          :address_id     People$UUID}))

  (is (= (scm/schema People$Person :address :home/house)
         {:num_rooms Integer/TYPE}))

  (is (= (scm/schema People$Person)
         {:id                   Integer/TYPE
          :name                 String
          :email                String
          :address              People$Address
          :likes                [People$Like]
          :relations            {String People$Person}
          :pet_names            [String]
          :private_key          ByteString
          :age_millis           Long/TYPE
          :is_vegetarian        Boolean/TYPE
          :height_cm            Double/TYPE
          :weight_kg            Float/TYPE
          :levels               [#{"HIGH" "LOW" "MEDIUM" "ALIASED_HIGH"}]
          :social_security      com.google.protobuf.Int32Value
          :maiden_name          com.google.protobuf.StringValue
          :uuid                 People$UUID
          :bv                   com.google.protobuf.BytesValue
          :bla                  {String, com.google.protobuf.DoubleValue}
          :ids_list             [Integer]
          :relations_like_level {String #{"HIGH" "LOW" "MEDIUM" "ALIASED_HIGH"}}
          :thing/num            Integer/TYPE
          :thing/str            String
          :thing/person         People$Person
          :thing/level          #{"HIGH" "LOW" "MEDIUM" "ALIASED_HIGH"}
          :s2s                  {String String}
          :repeated_bytes       [ByteString]
          :repeated_bools       [Boolean]
          :repeated_floats      [Float]
          :repeated_doubles     [Double]})))


(deftest search-test []
  (is (= [[:id]] (scm/search (fn [k _v] (= :id k))  People$Person)))
  (is (= [[:uuid]
          [:address :address_id]]
         (scm/search
           (fn [_k v]
             (= protogen.generated.People$UUID v))
           People$Person))))
