(ns pronto.wrapper
  (:require [pronto.utils :as u]
            [pronto.transformations :as transform]
            [pronto.reflection :as reflect])
  (:import [com.google.protobuf ByteString
            Descriptors$FieldDescriptor
            Descriptors$EnumValueDescriptor]
           [pronto ProtoMap]))

(defprotocol Wrapper
  (wrap [this v])
  (unwrap [this v]))

(def ^:private numeric-scalar?
  (comp boolean #{Integer Integer/TYPE Long Long/TYPE Double Double/TYPE Float Float/TYPE}))

(defn- if-instrument [ctx test then else]
  (if-not (:instrument? ctx)
    then
    `(if ~test
       ~then
       ~else)))

(defn protobuf-scalar? [^Class clazz]
  (boolean (or (numeric-scalar? clazz)
               (#{Boolean Boolean/TYPE
                  Integer/TYPE Integer
                  Long/TYPE Long
                  String ByteString} clazz))))


(def ^:private wkt->java-type
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

(defn- make-error [^Class clazz ctx v]
  (u/make-type-error (:class ctx)
                     (.getName ^Descriptors$FieldDescriptor (:fd ctx))
                     clazz
                     v))


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
  (let [values        (reflect/enum-values clazz)
        enum-value-fn (or (:enum-value-fn ctx) identity)
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
        (let [v2 (gensym 'v)]
          `(let [~v2 ~v]
             (if (keyword? ~v2)
               (case ~v2
                 ~@(interleave
                     (map first kw->enum)
                     (map second kw->enum))
                 (throw ~(u/make-type-error (:class ctx)
                                            (u/field->kebab-case (:fd ctx))
                                            clazz
                                            v2)))
               ~(if-instrument ctx
                  `(instance? ~clazz ~v)
                  v2
                  `(throw ~(make-error clazz ctx v))))))))))



(defmethod gen-wrapper
  :message
  [^Class clazz ctx]
  (let [fqn?                   (get ctx :pronto/fqn? true)
        ns                     (when fqn? (str (:ns ctx) "."))
        wrapper-type           (u/class->map-class-name clazz)
        wrapper-type           (if ns (symbol (u/javaify (str ns wrapper-type))) wrapper-type)
        transient-wrapper-type (u/class->transient-class-name clazz)
        transient-wrapper-type (if ns (symbol (u/javaify (str ns transient-wrapper-type))) transient-wrapper-type)]
    (reify Wrapper
      (wrap [_ v]
        `(new ~wrapper-type ~v (meta ~v)))

      (unwrap [_ v]
        `(cond
           (identical? (class ~v) ~wrapper-type)
           ~(let [u (with-meta (gensym 'u) {:tag 'pronto.ProtoMap})]
              `(let [~u ~v]
                 (pronto.RT/getProto ~u)))

           (identical? (class ~v) ~clazz) ~v

;; TODO: consolidate this with first clause

           (instance? ProtoMap ~v)
           ~(let [u (with-meta (gensym 'u) {:tag 'pronto.ProtoMap})]
              `(let [~u ~v]
                 (pronto.RT/getProto ~u)))

           (map? ~v)
           ;; TODO: duplicate code
           ~(let [u (with-meta (gensym 'u) {:tag 'pronto.ProtoMap})]
              `(let [~u (transform/map->proto-map (new ~transient-wrapper-type (~(u/static-call clazz "newBuilder")) true)  ~v)]
                 (pronto.RT/getProto ~u)))

           :else ~(when (:instrument? ctx) `(throw ~(make-error clazz ctx v))))))))


;; TODO: Get rid of this method, collapse into regular scalar
(defmethod gen-wrapper
  :bytes
  [_ ctx]
  ;; the class must be `ByteString`
  (reify Wrapper
    (wrap [_ v]
      v)

    (unwrap [_ v]
      (if-instrument ctx
        `(instance? com.google.protobuf.ByteString ~v)
        v
        `(throw ~(make-error com.google.protobuf.ByteString ctx v))))))


(defmethod gen-wrapper
  :scalar
  [^Class clazz ctx]
  (reify Wrapper
    (wrap [_ v] v)

    (unwrap [_ v]
      ;; TODO: clean this up...
      (cond
        (= String clazz)
        (if-instrument ctx
                       `(= String (class ~v))
                       v
                       `(throw ~(make-error clazz ctx v)))
        (or (= Boolean/TYPE clazz) (= Boolean clazz))
        (if-instrument ctx
                       `(= Boolean (class ~v))
                       v
                       `(throw ~(make-error clazz ctx v)))
        (numeric-scalar? clazz)
        (let [boxed?          (get #{Long Integer Double Float} clazz)
              primitive-class (if boxed?
                                (condp = clazz
                                  Integer Integer/TYPE
                                  Long    Long/TYPE
                                  Double  Double/TYPE
                                  Float   Float/TYPE)
                                clazz)
              vn              (if boxed?
                                (u/with-type-hint v Number)
                                v)]
          (if boxed?
            (if-instrument ctx
              `(instance? Number ~vn)
              `(~(symbol (str "." (str primitive-class) "Value")) ~vn)
              `(throw ~(make-error clazz ctx v)))
            vn))
        :else (throw (IllegalArgumentException. (str "don't know how to wrap " clazz)))))))

