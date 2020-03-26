(ns pronto.utils
  (:require [clojure.string :as s]))


(defn sanitized-class-name [^Class clazz]
  (s/replace (.getName clazz) "." "-"))

(defn class-name->wrapper-class-name [^Class clazz]
  (symbol (str 'wrapped- (sanitized-class-name clazz))))

(defn class-name->transient-class-name [^Class clazz]
  (symbol (str 'transient- (sanitized-class-name clazz))))

(defn transient-ctor-name [^Class clazz]
  (symbol (str '-> (class-name->transient-class-name clazz))))

(defn ->kebab-case [s]
  (s/lower-case (s/join "-" (s/split s #"_"))))

(defn with-type-hint [sym ^Class clazz]
  (with-meta sym {:tag (symbol (.getName clazz))}))


(defn ctor-name [^Class clazz]
  (symbol (str 'proto-> (s/replace (.getName clazz) "." "-"))))

