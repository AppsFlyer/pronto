(ns pronto.schema
  (:require [pronto.reflection :as reflect]
            [pronto.type-gen :as t]
            [pronto.utils :as u]
            [pronto.wrapper :as w])
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


(defn- find-descriptors [clazz descriptors key-path]
  (loop [clazz       clazz
         descriptors descriptors
         ks          key-path]
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


(defn- search-descriptors [kw descriptors]
  (when kw
    (some
     (fn [^Descriptors$FieldDescriptor fd]
       (when (= (.getName fd) (name kw))
         fd))
     descriptors)))


(defn- struct-schema [clazz descriptors]
  (when (seq descriptors)
    (into
     {}
     (map
      (fn [^Descriptors$FieldDescriptor fd]
        [(keyword
          (when-let [oneof (.getContainingOneof fd)]
            (.getName oneof))
          (.getName fd))
         (field-schema clazz fd)]))
     descriptors)))


(defn- class-descriptors [clazz ks]
  (find-descriptors
   clazz
   (map :fd (t/get-fields clazz {}))
   ks))


(defn- schema* [clazz ks]
  (let [k (last ks)
        [clazz descriptors] (class-descriptors clazz (butlast ks))]
    (if k
      (when-let [d (search-descriptors k descriptors)]
        (let [t (t/find-type clazz d)]
          (if (w/protobuf-scalar? t)
            (field-schema clazz d)
            (struct-schema t (second (class-descriptors t []))))))
      (struct-schema clazz descriptors))))


(defn schema
  "Accepts a proto-map or class of a POJO, and returns a schema as a map.
  If `ks` not supplied, will return the schema of the given class. Otherwise, will drill down to the schema using `ks` as a path"
  [proto-map-or-class & ks]
  (schema*
   (cond
     (class? proto-map-or-class)     proto-map-or-class
     (u/proto-map? proto-map-or-class) (class (u/proto-map->proto proto-map-or-class)))
   ks))


(defn- search* [f clazz path seen-classes]
  (let [schema (schema* clazz [])
        seen-classes (conj seen-classes clazz)
        res (->> schema
                 (filter (fn [[k v]] (f k v)))
                 keys
                 (map #(conj path %)))
        sub-res (->> schema
                     (filter
                      (fn [[_ v]]
                        (and (class? v)
                             (not (w/protobuf-scalar? v))
                             (not (get seen-classes v)))))
                     (mapcat
                      (fn [[k v]]
                        (search* f v (conj path k) seen-classes))))]
    (concat res sub-res)))


(defn search
  "Applies `f`, a predicate, to every field in the schema of the supplied class, recursively, and
  returns a seq of paths to fields for which the function returned a truthy value.
  `f` must be a 2 arity function, receiving the field name (keyword) and its type as given in `pronto.schema/schema`."
  [f clazz]
  (search* f clazz [] #{}))
