(ns pronto.schema
  (:require [pronto.reflection :as reflect]
            [pronto.type-gen :as t])
  (:import  [com.google.protobuf
             Descriptors$FieldDescriptor
             Descriptors$Descriptor]))

(defn- type-info [^Class clazz]
  (cond
    (reflect/enum? clazz) (into #{} (map str) (reflect/enum-values clazz))
    :else                 clazz))


(defn- field-schema [^Class clazz ^Descriptors$FieldDescriptor descriptor]
  (cond
    (.isMapField descriptor) (let [{:keys [key-type val-type]} (t/map-type-info clazz descriptor)]
                               {(type-info key-type)
                                (type-info val-type)})
    (.isRepeated descriptor) [(type-info (t/repeated-type-info clazz descriptor))]
    :else                    (let [^Class x (t/field-type clazz descriptor)]
                               (type-info x))))


(defn- find-descriptors [clazz descriptors ks]
  (loop [clazz       clazz
         descriptors descriptors
         ks          ks]
    (if-not (seq ks)
      [clazz descriptors]
      (let [[k & ks] ks
            ^Descriptors$FieldDescriptor descriptor
            (some
             (fn [^Descriptors$FieldDescriptor d]
               (when (= (name k) (.getName d))
                 d))
             descriptors)]
        (when descriptor
          (let [sub-descs (.getFields ^Descriptors$Descriptor (.getMessageType descriptor))
                clazz     (t/field-type clazz descriptor)]
            (recur clazz sub-descs ks)))))))

(defn schema [clazz ks]
  (let [[clazz descriptors] (find-descriptors
                             clazz
                             (map :fd (t/get-fields clazz {}))
                             ks)]
    (when descriptors
      (into {}
            (map
             (fn [^Descriptors$FieldDescriptor fd]
               [(keyword
                 (when-let [oneof (.getContainingOneof fd)]
                   (.getName oneof))
                 (.getName fd))
                (field-schema clazz fd)]))
            descriptors))))
