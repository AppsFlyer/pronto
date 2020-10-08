(ns pronto.jmh.benchmarks
  (:require [pronto.core :as p])
  (:import [org.openjdk.jmh.infra Blackhole]
           [protogen.generated
            Benchmarks$Strings5
            Benchmarks$Strings10
            Benchmarks$Strings20
            Benchmarks$Strings30]))

(p/enable-instrumentation!)

(def ^String the-val "hello world")

(def clj-map20 (into {} (map (fn [i] [(keyword (str "field_" i)) the-val]) (range 0 20))))
(p/defproto Benchmarks$Strings20)
(def proto-map20 (p/clj-map->proto-map Benchmarks$Strings20 clj-map20))

(defn assocs20_1 [^Blackhole bh field]
  (.consume bh ^Object (assoc proto-map20 field the-val) #_(p/p-> proto-map20 (assoc :field_0 the-val))))

(defn clj-assocs20_1 [^Blackhole bh field]
  (.consume bh ^Object (assoc clj-map20 field the-val)))


(defn assocs20_2 [^Blackhole bh]
  (.consume bh ^Object (p/p-> proto-map20
                             (assoc :field_0 the-val)
                             (assoc :field_1 the-val))))


(defn assocs20_3 [^Blackhole bh]
  (.consume bh ^Object (p/p-> proto-map20
                             (assoc :field_0 the-val)
                             (assoc :field_1 the-val)
                             (assoc :field_2 the-val))))


(defn assocs20_5 [^Blackhole bh]
  (.consume bh ^Object (p/p-> proto-map20
                             (assoc :field_1 the-val)
                             (assoc :field_2 the-val)
                             (assoc :field_3 the-val)
                             (assoc :field_4 the-val)
                             (assoc :field_5 the-val))))


(defn assocs20_10 [^Blackhole bh]
  (.consume bh ^Object (p/p-> proto-map20
                              (assoc :field_1 the-val)
                              (assoc :field_2 the-val)
                              (assoc :field_3 the-val)
                              (assoc :field_4 the-val)
                              (assoc :field_5 the-val)
                              (assoc :field_6 the-val)
                              (assoc :field_7 the-val)
                              (assoc :field_8 the-val)
                              (assoc :field_9 the-val)
                              (assoc :field_10 the-val))))

(defn clj-get20 [^Blackhole bh]
  (.consume bh ^Object (get clj-map20 :field_1)))

(defn get20 [^Blackhole bh]
  (.consume bh ^Object (get proto-map20 :field_1)))

(defn rget20 [^Blackhole bh]
  (.consume bh ^Object (p/p-> proto-map20 :field_1)))

;;;;;;;;;

(def clj-map10 (into {} (map (fn [i] [(keyword (str "field_" i)) the-val]) (range 0 10))))
(p/defproto Benchmarks$Strings10)
(def proto-map10 (p/clj-map->proto-map Benchmarks$Strings10 clj-map10))

(defn assocs10_1 [^Blackhole bh field]
  (.consume bh ^Object (assoc proto-map10 field the-val)))

(defn clj-assocs10_1 [^Blackhole bh field]
  (.consume bh ^Object (assoc clj-map10 field the-val)))


(defn assocs10_2 [^Blackhole bh]
  (.consume bh ^Object (p/p-> proto-map10
                              (assoc :field_0 the-val)
                              (assoc :field_1 the-val))))



(defn assocs10_3 [^Blackhole bh]
  (.consume bh ^Object (p/p-> proto-map10
                              (assoc :field_0 the-val)
                              (assoc :field_1 the-val)
                              (assoc :field_2 the-val))))



(defn assocs10_5 [^Blackhole bh]
  (.consume bh ^Object (p/p-> proto-map10
                              (assoc :field_1 the-val)
                              (assoc :field_2 the-val)
                              (assoc :field_3 the-val)
                              (assoc :field_4 the-val)
                              (assoc :field_5 the-val))))


(defn assocs10_10 [^Blackhole bh]
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

(defn clj-get10 [^Blackhole bh]
  (.consume bh ^Object (get clj-map10 :field_1)))

(defn get10 [^Blackhole bh]
  (.consume bh ^Object (get proto-map10 :field_1)))

(defn rget10 [^Blackhole bh]
  (.consume bh ^Object (p/p-> proto-map10 :field_1)))

;;;;;;;;;

(def clj-map5 (into {} (map (fn [i] [(keyword (str "field_" i)) the-val]) (range 0 5))))
(p/defproto Benchmarks$Strings5)
(def proto-map5 (p/clj-map->proto-map Benchmarks$Strings5 clj-map5))

(defn assocs5_1 [^Blackhole bh field]
  (.consume bh ^Object (assoc proto-map5 field the-val)))

(defn clj-assocs5_1 [^Blackhole bh field]
  (.consume bh ^Object (assoc clj-map5 field the-val)))


(defn assocs5_2 [^Blackhole bh]
  (.consume bh ^Object (p/p-> proto-map5
                              (assoc :field_0 the-val)
                              (assoc :field_1 the-val))))



(defn assocs5_3 [^Blackhole bh]
  (.consume bh ^Object (p/p-> proto-map5
                              (assoc :field_0 the-val)
                              (assoc :field_1 the-val)
                              (assoc :field_2 the-val))))



(defn assocs5_5 [^Blackhole bh]
  (.consume bh ^Object (p/p-> proto-map5
                              (assoc :field_0 the-val)
                              (assoc :field_1 the-val)
                              (assoc :field_2 the-val)
                              (assoc :field_3 the-val)
                              (assoc :field_4 the-val))))


(defn clj-get5 [^Blackhole bh]
  (.consume bh ^Object (get clj-map5 :field_1)))

(defn get5 [^Blackhole bh]
  (.consume bh ^Object (get proto-map5 :field_1)))

(defn rget5 [^Blackhole bh]
  (.consume bh ^Object (p/p-> proto-map5 :field_1)))
