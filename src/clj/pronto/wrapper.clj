(ns pronto.wrapper
  (:require [pronto.utils :as u]
            [pronto.transformations :as transform])
  (:import [clojure.lang Reflector]
           [com.google.protobuf ByteString
            Descriptors$FieldDescriptor
            Descriptors$EnumDescriptor
            Descriptors$EnumValueDescriptor]))

(defprotocol Wrapper
  (wrap [this v])
  (unwrap [this v]))

(def numeric-scalar?
  (comp boolean #{Integer Integer/TYPE Long Long/TYPE Double Double/TYPE Float Float/TYPE}))



(defn protobuf-scalar? [^Class clazz]
  (boolean (or (numeric-scalar? clazz)
               (#{Boolean Boolean/TYPE
                  Integer/TYPE Integer
                  Long/TYPE Long
                  String ByteString} clazz))))


(def wkt->java-type
  ;; use string keys since these classes may not be loaded by the classloader
  {"com.google.protobuf.DoubleValue" Double/TYPE
   "com.google.protobuf.FloatValue"  Float/TYPE
   "com.google.protobuf.Int64Value"  Long/TYPE
   "com.google.protobuf.UInt64Value" Long/TYPE
   "com.google.protobuf.StringValue" String
   "com.google.protobuf.Int32Value"  Integer/TYPE
   "com.google.protobuf.UInt32Value" Integer/TYPE
   "com.google.protobuf.BoolValue"   Boolean/TYPE
   "com.google.protobuf.BytesValue"  ByteString})

(defmulti gen-wrapper
  (fn [^Class clazz ctx]
    (cond
      (get (:encoders ctx) clazz)       :custom
      (.isEnum clazz)                   :enum
      (= ByteString clazz)              :bytes
      (wkt->java-type (.getName clazz)) :well-known-type
      (not (protobuf-scalar? clazz))    :message
      :else                             :scalar)))

(defn make-error [^Class clazz ctx v]
  `(u/make-type-error ~(:class ctx)
                      ~(.getName ^Descriptors$FieldDescriptor (:fd ctx))
                      ~clazz
                      ~v))


(defmethod gen-wrapper
  :custom
  [^Class clazz ctx]
  (let [{:keys [from-proto to-proto]} (get (:encoders ctx) clazz)]
    (reify Wrapper
      (wrap [_ v]
        `(~from-proto ~v))
      (unwrap [_ v]
        `(~to-proto ~v)))))

(defmethod gen-wrapper
  :well-known-type
  [^Class clazz ctx]
  (let [java-type (wkt->java-type (.getName clazz))
        wrapper   (gen-wrapper java-type ctx)]
    (reify Wrapper
      (wrap [_ v]
        (wrap wrapper `(.getValue ~(u/with-type-hint v clazz))))
      (unwrap [_ v]
        `(let [b# (~(u/static-call clazz "newBuilder"))]
           (.setValue b# ~(unwrap wrapper v))
           (.build b#))))))

(defmethod gen-wrapper
  :enum
  [^Class clazz ctx]
  (let [descriptor    (Reflector/invokeStaticMethod clazz "getDescriptor" (to-array nil))
        values        (.getValues ^Descriptors$EnumDescriptor descriptor)
        enum-value-fn (:enum-value-fn ctx)
        enum->kw      (map-indexed #(vector %1 (keyword (enum-value-fn (.getName ^Descriptors$EnumValueDescriptor %2))))
                                   values)
        kw->enum      (map #(vector (keyword (enum-value-fn (.getName ^Descriptors$EnumValueDescriptor %1)))
                                    (symbol (str (.getName clazz) "/" (.getName ^Descriptors$EnumValueDescriptor %1))))
                           values)]
    (reify Wrapper
      (wrap [_ v]
        `(case (.ordinal ~(u/with-type-hint v clazz))
           ~@(interleave
               (map first enum->kw)
               (map second enum->kw))
           (throw (IllegalArgumentException. (str "can't wrap " ~v)))))

      (unwrap [_ v]
        `(case ~v
           ~@(interleave
               (map first kw->enum)
               (map second kw->enum))
           (throw (throw (u/make-enum-error ~(:class ctx)
                                            ~(u/field->kebab-case (:fd ctx))
                                            ~clazz
                                            ~v))))))))


(defn make-error-message ^String [expected-class value]
  (str "expected " expected-class ", but got " (or (class value) "nil")))

(defmethod gen-wrapper
  :message
  [^Class clazz ctx]
  (let [wrapper-type           (u/class->map-class-name clazz)
        transient-wrapper-type (u/class->transient-class-name clazz)]
    (reify Wrapper
      (wrap [_ v]
        `(new ~wrapper-type ~v (meta ~v)))

      (unwrap [_ v]
        `(cond
           (= (class ~v) ~clazz) ~v

           (= (class ~v) ~wrapper-type)
           ~(let [u (with-meta (gensym 'u) {:tag 'pronto.ProtoMap})]
              `(let [~u ~v]
                 (pronto.RT/getProto ~u)))

           (map? ~v)
           ;; TODO: duplicate code
           ~(let [u (with-meta (gensym 'u) {:tag 'pronto.ProtoMap})]
              `(let [~u (transform/map->proto-map (new ~transient-wrapper-type (~(u/static-call clazz "newBuilder")) true)  ~v)]
                 (pronto.RT/getProto ~u)))

           :else (throw ~(make-error clazz ctx v)))))))


;; TODO: Get rid of this method, collapse into regular scalar
(defmethod gen-wrapper
  :bytes
  [_ ctx]
  ;; the class must be `ByteString`
  (reify Wrapper
    (wrap [_ v]
      v)

    (unwrap [_ v]
      `(if (instance? com.google.protobuf.ByteString ~v)
         ~v
         (throw ~(make-error com.google.protobuf.ByteString ctx v))))))


(defmethod gen-wrapper
  :scalar
  [^Class clazz ctx]
  (reify Wrapper
    (wrap [_ v] v)

    (unwrap [_ v]
      ;; TODO: clean this up...
      (cond
        (= String clazz)
        `(if-not (= String (class ~v))
           (throw ~(make-error clazz ctx v))
           ~v)
        (= Boolean/TYPE clazz)
        `(if-not (= Boolean (class ~v))
           (throw ~(make-error clazz ctx v))
           ~v)
        (numeric-scalar? clazz)
        (let [vn (u/with-type-hint v Number)]
          `(if-not (instance? Number ~vn)
             (throw ~(make-error clazz ctx v))
             (let [~vn ~v]
               (~(symbol (str "." (str clazz) "Value")) ~vn))))
        :else (throw (IllegalArgumentException. (str "don't know how to wrap " clazz)))))))

