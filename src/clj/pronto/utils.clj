(ns pronto.utils
  (:require [clojure.string :as s]
            [pronto.protos :refer [global-ns]])
  (:import
   [pronto ProtoMap ProtoMapper] 
   [com.google.protobuf
    Descriptors$FieldDescriptor
    Descriptors$GenericDescriptor
    Descriptors$FieldDescriptor$Type
    GeneratedMessageV3]))


(defn javaify [s] (s/replace s "-" "_"))

(defn normalize-path [s]
  (-> s
      (s/replace "." "_")
      (s/replace "$" "__")))

(defn sanitized-class-name [^Class clazz]
  (normalize-path (.getName clazz)))

(defn class->map-class-name [^Class clazz]
  (symbol (str (sanitized-class-name clazz) "ProtoMap")))

(defn class->abstract-map-class-name [^Class clazz]
  (symbol (str (sanitized-class-name clazz) "AbstractMap")))

(defn class->abstract-persistent-map-class-name [^Class clazz]
  (symbol (str (sanitized-class-name clazz) "AbstractPersistentMap")))


(defn class->transient-class-name [^Class clazz]
  (symbol (str 'transient_ (sanitized-class-name clazz))))


(defn ->kebab-case
  "Converts `s`, assumed to be in snake_case, to kebab-case"
  [^String s]
  (when s
    (s/lower-case (.replace s \_ \-))))


(defn with-type-hint [sym ^Class clazz]
  (with-meta sym {:tag (symbol (.getName clazz))}))

(defn ctor-name [prefix ^Class clazz]
  (symbol (str prefix '-> (class->map-class-name clazz))))

(defn ->camel-case
  "Implements protobuf's camel case conversion for Java. See: https://github.com/protocolbuffers/protobuf/blob/v3.12.4/src/google/protobuf/compiler/java/java_helpers.cc#L157"
  [^String s]
  (when-let [length (some-> s .length)]
    (loop [i                 0
           ^StringBuilder sb (StringBuilder.)
           cap-next-letter?  true]
      (if (= i length)
        (.toString sb)
        (let [x (.charAt s i)]
          (cond
            (Character/isLowerCase x)
            (recur (inc i)
                   (.append sb (if cap-next-letter? (Character/toUpperCase x) x))
                   false)
            (Character/isUpperCase x)
            (recur (inc i) (.append sb x) false)
            (Character/isDigit x)
            (recur (inc i) (.append sb x) true)
            :else
            (recur (inc i) sb true)))))))

(defn field->camel-case [^Descriptors$GenericDescriptor field]
  (->camel-case (.getName field)))

(defn field->kebab-case [^Descriptors$GenericDescriptor field]
  (->kebab-case (.getName field)))

(defn message? [^Descriptors$FieldDescriptor fd]
  (= (.getType fd)
     Descriptors$FieldDescriptor$Type/MESSAGE))


(defn struct? [^Descriptors$FieldDescriptor fd]
  (and (message? fd)
       (not (.isMapField fd))
       (not (.isRepeated fd))))

(defn optional? [^Descriptors$FieldDescriptor fd]
  (.hasOptionalKeyword fd))

(defn enum? [^Descriptors$FieldDescriptor fd]
  (= (.getType fd)
     Descriptors$FieldDescriptor$Type/ENUM))

(defn static-call [^Class class method-name]
  (symbol (str (.getName class) "/" method-name)))


(defn type-error-info [clazz field-name expected-type value]
  {:class         clazz
   :field         field-name
   :expected-type expected-type
   :value         value})

(defn make-type-error
  ([clazz field-name expected-type value]
   (make-type-error clazz field-name expected-type value nil))
  ([clazz field-name expected-type value cause]
  ;; return as code so this frame isn't included in the stack trace
   `(ex-info "Invalid type" ~(type-error-info clazz field-name expected-type value) ~cause)))



(defmacro with-ns [new-ns & body]
  (let [orig-ns          *ns*
        orig-ns-name     (ns-name orig-ns)
        ns-name-sym      (symbol new-ns)
        existing-classes (set (when-let [n (find-ns ns-name-sym)]
                                (vals (ns-imports n))))]
    (if (or (nil? new-ns)
            (= new-ns (str *ns*)))
      body
      `(do
         (create-ns (quote ~ns-name-sym))
         (in-ns (quote ~ns-name-sym))
         ~@(for [[_ ^Class clazz]
                 (ns-imports orig-ns)
                 :let  [class-name (.getName clazz)]
                 :when (not (get existing-classes clazz))
                 ;; No point to import POJO classes, and this can also
                 ;; lead to conflicts if 2 namespaces import 2 classes
                 ;; with the same name but different packages.
                 :when (not= (.getSuperclass clazz) GeneratedMessageV3)
                 ;; don't import generated classes created by the lib, as this might
                 ;; lead to collision between different mappers when importing
                 ;; these classes into the global ns
                 :when (not (s/starts-with? class-name (javaify global-ns)))]
             `(import ~(symbol (.getName clazz))))
         ;; clojure.core is not auto-loaded so load it explicitly
         ;; in order for any of its vars to be resolvable
         (use '[clojure.core])
         ~@body
         #_(finally)
         (in-ns (quote ~(symbol orig-ns-name)))))))


(defn- split' [f coll]
  (loop [[x & xs :as c] coll
         res            []]
    (if-not x
      res
      (if (f x)
        (recur
          xs
          (conj res x))
        (let [[a b] (split-with (complement f) c)]
          (recur
            b
            (conj res a)))))))


(def leaf-val :val)

(defn leaf [x] (with-meta {:val x} {::leaf? true}))

(def leaf? (comp boolean ::leaf? meta))


(defn kv-forest [kvs]
  (loop [[kv-partition & ps] (partition-by ffirst kvs)
         res                 []]
    (if-not kv-partition
      res
      (let [leader-key   (first (ffirst kv-partition))
            follower-kvs (->> kv-partition
                              (map
                                (fn [[ks v]]
                                  (let [rks (rest ks)]
                                    (if (seq rks)
                                      (vector rks v)
                                      (leaf v)))))
                              (split' leaf?))]
        (recur
          ps
          (conj
            res
            [leader-key
             (mapcat
               (fn [g]
                 (if (leaf? g)
                   [g]
                   (kv-forest g)))
               follower-kvs)]))))))


(defn- flatten-forest* [forest]
  (if-not (seq forest)
    []
    (for [[k tree] forest
          v        tree]
      (if (leaf? v)
        [[k] (leaf-val v)]
        (mapcat
          (fn [[k' v']]
            [(cons k k') v'])
          (flatten-forest* [v]))))))


(defn flatten-forest [forest]
  (partition 2 (apply concat (flatten-forest* forest))))


(defn safe-resolve [x]
  (try
    (resolve x)
    (catch Exception _)))


(defn proto-map? [m]
  (instance? ProtoMap m))


(defn proto-map->proto
  "Returns the protobuf instance associated with the proto-map"
  [^ProtoMap m]
  (.pmap_getProto m))


(defn mapper? [m]
  (instance? ProtoMapper m))
