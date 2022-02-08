(ns pronto.jmh.benchmarks
  (:require [pronto.core :as p])
  (:import [org.openjdk.jmh.infra Blackhole]
           [protogen.generated
            Benchmarks$Strings5
            Benchmarks$Strings10
            Benchmarks$Strings20
            Benchmarks$Strings30
            Benchmarks$Strings50
            Benchmarks$Strings5$Builder
            Benchmarks$Strings20$Builder
            Benchmarks$Strings10$Builder]))


(def the-val "hello world")



(p/defmapper mapper [Benchmarks$Strings5
                     Benchmarks$Strings10
                     Benchmarks$Strings20
                     Benchmarks$Strings30
                     Benchmarks$Strings50])

(def clj-map20 (into {} (map (fn [i] [(keyword (str "field_" i)) the-val]) (range 0 20))))

(def proto-map20 (p/clj-map->proto-map mapper Benchmarks$Strings20 clj-map20))
(def ^Benchmarks$Strings20 proto20 (p/proto-map->proto proto-map20))


(def clj-map10 (into {} (map (fn [i] [(keyword (str "field_" i)) the-val]) (range 0 10))))

(def proto-map10 (p/clj-map->proto-map mapper Benchmarks$Strings10 clj-map10))
(def ^Benchmarks$Strings10 proto10 (p/proto-map->proto proto-map10))


(def clj-map5 (into {} (map (fn [i] [(keyword (str "field_" i)) the-val]) (range 0 5))))

(def proto-map5 (p/clj-map->proto-map mapper Benchmarks$Strings5 clj-map5))
(def ^Benchmarks$Strings5 proto5 (p/proto-map->proto proto-map5))

;;;;;;;;;;;;;;;;;;;;;;;;;

(defn assocs5_1_clj [^Blackhole bh]
  (.consume bh ^Object
            (-> clj-map5
                (assoc :field_0 the-val))))


(defn assocs5_1_protomap [^Blackhole bh]
  (.consume bh ^Object (-> proto-map5
                           (assoc :field_0 the-val))))


(defn assocs5_1_java [^Blackhole bh]
  (.consume bh ^Object
            (let [^Benchmarks$Strings5$Builder builder (.toBuilder proto5)]
              (.setField0 builder the-val)
              (.build builder))))

;;;;;;;;;;;;;;;;;;;;;;;;;

(defn assocs20_1_clj [^Blackhole bh]
  (.consume bh ^Object
            (-> clj-map20
                (assoc :field_0 the-val))))


(defn assocs20_1_java [^Blackhole bh]
  (.consume bh ^Object
            (let [^Benchmarks$Strings20$Builder builder (.toBuilder proto20)]
              (.setField0 builder the-val)
              (.build builder))))

(defn assocs20_2_clj [^Blackhole bh]
  (.consume bh ^Object
            (-> clj-map20
                (assoc :field_0 the-val)
                (assoc :field_1 the-val))))


(defn assocs20_1_protomap [^Blackhole bh]
  (.consume bh ^Object (-> proto-map20
                           (assoc :field_0 the-val))))

(defn assocs20_2 [^Blackhole bh]
  (.consume bh ^Object (-> proto-map20
                           (assoc :field_0 the-val)
                           (assoc :field_1 the-val))))


(defn assocs20_2_parrow [^Blackhole bh]
  (.consume bh ^Object (p/p-> proto-map20
                              (assoc :field_0 the-val)
                              (assoc :field_1 the-val))))

(defn assocs20_2_hinted [^Blackhole bh]
  (.consume bh ^Object
            (p/with-hints
              [(p/hint proto-map20 Benchmarks$Strings20 mapper)]
              (p/p-> proto-map20
                     (assoc :field_0 the-val)
                     (assoc :field_1 the-val)))))


(defn assocs20_2_java [^Blackhole bh]
  (.consume bh ^Object
            (let [^Benchmarks$Strings20$Builder builder (.toBuilder proto20)]
              (.setField0 builder the-val)
              (.setField1 builder the-val)
              (.build builder))))


;;;;;;;;;;;;;;;;;;;;

(defn assocs20_5_clj [^Blackhole bh]
  (.consume bh ^Object
            (-> clj-map20
                (assoc :field_0 the-val)
                (assoc :field_1 the-val)
                (assoc :field_2 the-val)
                (assoc :field_3 the-val)
                (assoc :field_4 the-val))))



(defn assocs20_5 [^Blackhole bh]
  (.consume bh ^Object (-> proto-map20
                           (assoc :field_0 the-val)
                           (assoc :field_1 the-val)
                           (assoc :field_2 the-val)
                           (assoc :field_3 the-val)
                           (assoc :field_4 the-val))))


(defn assocs20_5_parrow [^Blackhole bh]
  (.consume bh ^Object (p/p-> proto-map20
                              (assoc :field_0 the-val)
                              (assoc :field_1 the-val)
                              (assoc :field_2 the-val)
                              (assoc :field_3 the-val)
                              (assoc :field_4 the-val))))

(defn assocs20_5_hinted [^Blackhole bh]
  (.consume bh ^Object
            (p/with-hints
              [(p/hint proto-map20 Benchmarks$Strings20 mapper)]
              (p/p-> proto-map20
                     (assoc :field_0 the-val)
                     (assoc :field_1 the-val)
                     (assoc :field_2 the-val)
                     (assoc :field_3 the-val)
                     (assoc :field_4 the-val)))))


(defn assocs20_5_java [^Blackhole bh]
  (.consume bh ^Object
            (let [^Benchmarks$Strings20$Builder builder (.toBuilder proto20)]
              (.setField0 builder the-val)
              (.setField1 builder the-val)
              (.setField2 builder the-val)
              (.setField3 builder the-val)
              (.setField4 builder the-val)
              (.build builder))))


;;;;;;;;;;;;;;;;;;;



(defn assocs20_10_clj [^Blackhole bh]
  (.consume bh ^Object
            (-> clj-map20
                (assoc :field_0 the-val)
                (assoc :field_1 the-val)
                (assoc :field_2 the-val)
                (assoc :field_3 the-val)
                (assoc :field_4 the-val)
                (assoc :field_5 the-val)
                (assoc :field_6 the-val)
                (assoc :field_7 the-val)
                (assoc :field_8 the-val)
                (assoc :field_9 the-val))))



(defn assocs20_10 [^Blackhole bh]
  (.consume bh ^Object (-> proto-map20
                           (assoc :field_0 the-val)
                           (assoc :field_1 the-val)
                           (assoc :field_2 the-val)
                           (assoc :field_3 the-val)
                           (assoc :field_4 the-val)
                           (assoc :field_5 the-val)
                           (assoc :field_6 the-val)
                           (assoc :field_7 the-val)
                           (assoc :field_8 the-val)
                           (assoc :field_9 the-val))))


(defn assocs20_10_parrow [^Blackhole bh]
  (.consume bh ^Object (p/p-> proto-map20
                              (assoc :field_0 the-val)
                              (assoc :field_1 the-val)
                              (assoc :field_2 the-val)
                              (assoc :field_3 the-val)
                              (assoc :field_4 the-val)
                              (assoc :field_5 the-val)
                              (assoc :field_6 the-val)
                              (assoc :field_7 the-val)
                              (assoc :field_8 the-val)
                              (assoc :field_9 the-val))))

(defn assocs20_10_hinted [^Blackhole bh]
  (.consume bh ^Object
            (p/with-hints
              [(p/hint proto-map20 Benchmarks$Strings20 mapper)]
              (p/p-> proto-map20
                     (assoc :field_0 the-val)
                     (assoc :field_1 the-val)
                     (assoc :field_2 the-val)
                     (assoc :field_3 the-val)
                     (assoc :field_4 the-val)
                     (assoc :field_5 the-val)
                     (assoc :field_6 the-val)
                     (assoc :field_7 the-val)
                     (assoc :field_8 the-val)
                     (assoc :field_9 the-val)))))


(defn assocs20_10_java [^Blackhole bh]
  (.consume bh ^Object
            (let [^Benchmarks$Strings20$Builder builder (.toBuilder proto20)]
              (.setField0 builder the-val)
              (.setField1 builder the-val)
              (.setField2 builder the-val)
              (.setField3 builder the-val)
              (.setField4 builder the-val)
              (.setField5 builder the-val)
              (.setField6 builder the-val)
              (.setField7 builder the-val)
              (.setField8 builder the-val)
              (.setField9 builder the-val)
              (.build builder))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;;;;;;;;;;;;;;;;;;;;


(defn assocs10_1_clj [^Blackhole bh]
  (.consume bh ^Object
            (-> clj-map10
                (assoc :field_0 the-val))))


(defn assocs10_1_java [^Blackhole bh]
  (.consume bh ^Object
            (let [^Benchmarks$Strings10$Builder builder (.toBuilder proto10)]
              (.setField0 builder the-val)
              (.build builder))))


(defn assocs10_2_clj [^Blackhole bh]
  (.consume bh ^Object
            (-> clj-map10
                (assoc :field_0 the-val)
                (assoc :field_1 the-val))))



(defn assocs10_1_protomap [^Blackhole bh]
  (.consume bh ^Object (-> proto-map10
                           (assoc :field_0 the-val))))


(defn assocs10_2 [^Blackhole bh]
  (.consume bh ^Object (-> proto-map10
                           (assoc :field_0 the-val)
                           (assoc :field_1 the-val))))


(defn assocs10_2_parrow [^Blackhole bh]
  (.consume bh ^Object (p/p-> proto-map10
                              (assoc :field_0 the-val)
                              (assoc :field_1 the-val))))

(defn assocs10_2_hinted [^Blackhole bh]
  (.consume bh ^Object
            (p/with-hints
              [(p/hint proto-map10 Benchmarks$Strings10 mapper)]
              (p/p-> proto-map10
                     (assoc :field_0 the-val)
                     (assoc :field_1 the-val)))))


(defn assocs10_2_java [^Blackhole bh]
  (.consume bh ^Object
            (let [^Benchmarks$Strings10$Builder builder (.toBuilder proto10)]
              (.setField0 builder the-val)
              (.setField1 builder the-val)
              (.build builder))))


;;;;;;;;;;;;;;;;;;;;

(defn assocs10_5_clj [^Blackhole bh]
  (.consume bh ^Object
            (-> clj-map10
                (assoc :field_0 the-val)
                (assoc :field_1 the-val)
                (assoc :field_2 the-val)
                (assoc :field_3 the-val)
                (assoc :field_4 the-val))))



(defn assocs10_5 [^Blackhole bh]
  (.consume bh ^Object (-> proto-map10
                           (assoc :field_0 the-val)
                           (assoc :field_1 the-val)
                           (assoc :field_2 the-val)
                           (assoc :field_3 the-val)
                           (assoc :field_4 the-val))))


(defn assocs10_5_parrow [^Blackhole bh]
  (.consume bh ^Object (p/p-> proto-map10
                              (assoc :field_0 the-val)
                              (assoc :field_1 the-val)
                              (assoc :field_2 the-val)
                              (assoc :field_3 the-val)
                              (assoc :field_4 the-val))))

(defn assocs10_5_hinted [^Blackhole bh]
  (.consume bh ^Object
            (p/with-hints
              [(p/hint proto-map10 Benchmarks$Strings10 mapper)]
              (p/p-> proto-map10
                     (assoc :field_0 the-val)
                     (assoc :field_1 the-val)
                     (assoc :field_2 the-val)
                     (assoc :field_3 the-val)
                     (assoc :field_4 the-val)))))


(defn assocs10_5_java [^Blackhole bh]
  (.consume bh ^Object
            (let [^Benchmarks$Strings10$Builder builder (.toBuilder proto10)]
              (.setField0 builder the-val)
              (.setField1 builder the-val)
              (.setField2 builder the-val)
              (.setField3 builder the-val)
              (.setField4 builder the-val)
              (.build builder))))


;;;;;;;;;;;;;;;;;;;



(defn assocs10_10_clj [^Blackhole bh]
  (.consume bh ^Object
            (-> clj-map10
                (assoc :field_0 the-val)
                (assoc :field_1 the-val)
                (assoc :field_2 the-val)
                (assoc :field_3 the-val)
                (assoc :field_4 the-val)
                (assoc :field_5 the-val)
                (assoc :field_6 the-val)
                (assoc :field_7 the-val)
                (assoc :field_8 the-val)
                (assoc :field_9 the-val))))



(defn assocs10_10 [^Blackhole bh]
  (.consume bh ^Object (-> proto-map10
                           (assoc :field_0 the-val)
                           (assoc :field_1 the-val)
                           (assoc :field_2 the-val)
                           (assoc :field_3 the-val)
                           (assoc :field_4 the-val)
                           (assoc :field_5 the-val)
                           (assoc :field_6 the-val)
                           (assoc :field_7 the-val)
                           (assoc :field_8 the-val)
                           (assoc :field_9 the-val))))


(defn assocs10_10_parrow [^Blackhole bh]
  (.consume bh ^Object (p/p-> proto-map10
                              (assoc :field_0 the-val)
                              (assoc :field_1 the-val)
                              (assoc :field_2 the-val)
                              (assoc :field_3 the-val)
                              (assoc :field_4 the-val)
                              (assoc :field_5 the-val)
                              (assoc :field_6 the-val)
                              (assoc :field_7 the-val)
                              (assoc :field_8 the-val)
                              (assoc :field_9 the-val))))

(defn assocs10_10_hinted [^Blackhole bh]
  (.consume bh ^Object
            (p/with-hints
              [(p/hint proto-map10 Benchmarks$Strings10 mapper)]
              (p/p-> proto-map10
                     (assoc :field_0 the-val)
                     (assoc :field_1 the-val)
                     (assoc :field_2 the-val)
                     (assoc :field_3 the-val)
                     (assoc :field_4 the-val)
                     (assoc :field_5 the-val)
                     (assoc :field_6 the-val)
                     (assoc :field_7 the-val)
                     (assoc :field_8 the-val)
                     (assoc :field_9 the-val)))))


(defn assocs10_10_java [^Blackhole bh]
  (.consume bh ^Object
            (let [^Benchmarks$Strings10$Builder builder (.toBuilder proto10)]
              (.setField0 builder the-val)
              (.setField1 builder the-val)
              (.setField2 builder the-val)
              (.setField3 builder the-val)
              (.setField4 builder the-val)
              (.setField5 builder the-val)
              (.setField6 builder the-val)
              (.setField7 builder the-val)
              (.setField8 builder the-val)
              (.setField9 builder the-val)
              (.build builder))))

(defn assocs10_10_java_no_build [^Blackhole bh]
  (.consume bh ^Object
            (let [^Benchmarks$Strings10$Builder builder (.toBuilder proto10)]
              (.setField0 builder the-val)
              (.setField1 builder the-val)
              (.setField2 builder the-val)
              (.setField3 builder the-val)
              (.setField4 builder the-val)
              (.setField5 builder the-val)
              (.setField6 builder the-val)
              (.setField7 builder the-val)
              (.setField8 builder the-val)
              (.setField9 builder the-val))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn get10_java [^Blackhole bh]
  (.consume bh ^Object
            (.getField0 proto10)))

(defn get10_clj [^Blackhole bh]
  (.consume bh ^Object (get clj-map10 :field_0)))

(defn get10_protomap [^Blackhole bh]
  (.consume bh ^Object (get proto-map10 :field_0)))

(defn get10_hinted [^Blackhole bh]
  (.consume bh ^Object
            (p/with-hints
              [(p/hint proto-map10 Benchmarks$Strings10 mapper)]
              (p/p-> proto-map10 :field_0))))


(defn get5_java [^Blackhole bh]
  (.consume bh ^Object
            (.getField0 proto5)))

(defn get5_clj [^Blackhole bh]
  (.consume bh ^Object (get clj-map5 :field_0)))

(defn get5_protomap [^Blackhole bh]
  (.consume bh ^Object (get proto-map5 :field_0)))

(defn get5_hinted [^Blackhole bh]
  (.consume bh ^Object
            (p/with-hints
              [(p/hint proto-map5 Benchmarks$Strings5 mapper)]
              (p/p-> proto-map5 :field_0))))


(defn get20_java [^Blackhole bh]
  (.consume bh ^Object
            (.getField0 proto20)))

(defn get20_clj [^Blackhole bh]
  (.consume bh ^Object (get clj-map20 :field_0)))

(defn get20_protomap [^Blackhole bh]
  (.consume bh ^Object (get proto-map20 :field_0)))

(defn get20_hinted [^Blackhole bh]
  (.consume bh ^Object
            (p/with-hints
              [(p/hint proto-map20 Benchmarks$Strings20 mapper)]
              (p/p-> proto-map20 :field_0))))

