(ns pronto.utils
  (:require [clojure.string :as s]))


(defn sanitized-class-name [^Class clazz]
  (let [package-name (.getName (.getPackage clazz))
        class-name   (.getName clazz)]
    (subs class-name (inc (count package-name)))))

(defn class->map-class-name [^Class clazz]
  (symbol (str (sanitized-class-name clazz) "Map")))

(defn class->transient-class-name [^Class clazz]
  (symbol (str 'transient- (sanitized-class-name clazz))))

(defn transient-ctor-name [^Class clazz]
  (symbol (str '-> (class->transient-class-name clazz))))

(defn ->kebab-case [s]
  (s/lower-case (s/join "-" (s/split s #"_"))))

(defn with-type-hint [sym ^Class clazz]
  (with-meta sym {:tag (symbol (.getName clazz))}))

(defn ctor-name [prefix ^Class clazz]
  (symbol (str prefix '-> (class->map-class-name clazz))))

(def proto-ctor-name (partial ctor-name 'proto))

(def map-ctor-name (partial ctor-name 'map))

(def empty-ctor-name (partial ctor-name ""))

(def bytes-ctor-name (partial ctor-name 'bytes))


