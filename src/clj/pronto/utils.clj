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

(defn- char-in-range? [c start end]
  (<= (int start) (int c) (int end)))

(defn ->camel-case
  "Implements protobuf's camel case conversion for Java. See: https://github.com/protocolbuffers/protobuf/blob/v3.12.4/src/google/protobuf/compiler/java/java_helpers.cc#L157"
  [s]
  (loop [cc              ""
         [x & xs]        s
         cap-next-letter true]
    (if-not x
      cc
      (cond
        (char-in-range? x \a \z)
        (recur (str cc (if cap-next-letter
                         (char (+ (int x) (- (int \A) (int \a))))
                         x))
               xs
               false)
        (char-in-range? x \A \Z)
        (recur (str cc (if cap-next-letter
                         (char (+ (int x) (- (int \A) (int \a))))
                         x)) xs false)
        (char-in-range? x \0 \9)
        (recur (str cc x) xs true)
        :else
        (recur cc xs true)))))

(defn field->camel-case [^Descriptors$GenericDescriptor field]
  (->camel-case (.getName field)))

(defn message? [^Descriptors$FieldDescriptor fd]
  (= (.getType fd)
     Descriptors$FieldDescriptor$Type/MESSAGE))
