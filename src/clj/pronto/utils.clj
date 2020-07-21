(ns pronto.utils
  (:require [clojure.string :as s])
  (:import
   [com.google.protobuf
    Descriptors$FieldDescriptor
    Descriptors$GenericDescriptor
    Descriptors$FieldDescriptor$Type]))


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

(def json-ctor-name (partial ctor-name 'json))

(defn capitalize-camel-word [w]
  ;; TODO: redo this thing
  (let [numeric? (re-find #"\d" w)]
    (if numeric?
      (s/upper-case w)
      (s/capitalize w))))

(defn ->camel-case [s]
  (->> (s/split s #"_")
       (map capitalize-camel-word)
       (s/join "")))

(defn field->camel-case [^Descriptors$GenericDescriptor field]
  (->camel-case (.getName field)))

(defn message? [^Descriptors$FieldDescriptor fd]
  (= (.getType fd)
     Descriptors$FieldDescriptor$Type/MESSAGE))
