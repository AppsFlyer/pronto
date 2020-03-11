(ns pronto.core
  (:import [clojure.lang Reflector Associative APersistentMap ILookup Counted IPersistentMap
            MapEntry IPersistentCollection MapEquivalence Seqable ArrayIter ArraySeq]
           [protogen.generated People$Person People$Person$Builder
            People$Address People$Address$Builder People$Like People$Level]
           [com.google.protobuf Descriptors 
            Descriptors$Descriptor
            Descriptors$FieldDescriptor
            Descriptors$FieldDescriptor$Type
            Descriptors$FieldDescriptor$JavaType]
           [java.lang.reflect Field Method ParameterizedType]))


(defn descriptor [^Class clazz]
  (Reflector/invokeStaticMethod clazz "getDescriptor" (to-array nil)))

(defn field-descriptors [^Descriptors$Descriptor descriptor]
  (.getFields descriptor))

(defn field-name->keyword [field-name]
  (keyword field-name))

(defn field->camel-case [field]
  (->> (clojure.string/split (.getName field) #"_")
       (map #(clojure.string/capitalize %))
       (clojure.string/join "")))

(defn ->kebab-case [s]
  (clojure.string/lower-case (clojure.string/join "-" (clojure.string/split s #"_"))))

(defn fd->field-keyword [^Descriptors$FieldDescriptor fd]
  (keyword (.getName fd)))

(defn message? [^Descriptors$FieldDescriptor fd]
  (= (.getType fd)
     Descriptors$FieldDescriptor$Type/MESSAGE))

(defn fd->java-type [^Descriptors$FieldDescriptor fd]
  (if (message? fd)
    (Class/forName (.getFullName (.getMessageType fd)))
    (condp = (.getJavaType fd)
      Descriptors$FieldDescriptor$JavaType/INT Integer
      Descriptors$FieldDescriptor$JavaType/STRING String
      :else (throw (UnsupportedOperationException. (str "don't know type " (.getJavaType fd)))))))

(defn descriptor->deftype-class [^Descriptors$Descriptor descriptor]
  (symbol (str 'wrapped- (clojure.string/replace (.getFullName descriptor) "." "-"))))

(defn class-name->wrapper-class-name [class-name]
  (symbol (str 'wrapped- (clojure.string/replace class-name "." "-"))))

(defn fd->wrapper-class-name [^Descriptors$FieldDescriptor fd]
  (if (message? fd)
    (class-name->wrapper-class-name (.getFullName fd))
    (throw (IllegalArgumentException. (str "no wrapper type for " (.getType fd))))))

(defn primitive? [^Class clazz]
  (boolean (or (.isEnum clazz)
               (#{Integer
                  Integer/TYPE
                  String}
                clazz))))

(defn descriptor->deftype-class [^Descriptors$Descriptor descriptor]
  (symbol (str 'wrapped- (clojure.string/replace (.getFullName descriptor) "." "-"))))


(defn descriptor-type [^Descriptors$FieldDescriptor fd]
  (cond
    (.isMapField fd) :map
    (.isRepeated fd) :repeated
    (.getContainingOneof fd) :one-of
    :else :simple))

(defmulti get-type-gen
  (fn [^Class clazz
       ^Descriptors$FieldDescriptor$Type fd]
    (descriptor-type fd)))


(defprotocol TypeGen
  (get-class [this])
  (gen-setter [this o k v])
  (gen-getter [this o k]))

(defprotocol Wrapper
  (wrap [this v])
  (unwrap [this v]))

(defmulti gen-wrapper
  (fn [^Class clazz]    
    (cond
      (.isEnum clazz) :enum
      (not (primitive? clazz)) :message
      :else :primitive)))

(defmethod gen-wrapper
  :enum
  [^Class clazz]
  (let [enum->kw (map (fn [^Enum e] [(.name e) (keyword (->kebab-case (.name e)))])
                      (.getEnumConstants clazz))]
    ;; TODO: what about `unrecognized`?
    (reify Wrapper
      (wrap [_ v]
        `(case ~v
            ~@(interleave
               (map first enum->kw)
               (map second enum->kw))
            (throw (IllegalArgumentException. (str "don't know " ~v)))))
      (unwrap [_ v]
        `(case ~v
            ~@(interleave
               (map second enum->kw)
               (map first enum->kw))
            (throw (IllegalArgumentException. (str "don't know " ~v))))))))

(defmethod gen-wrapper
  :message
  [^Class clazz]
  (let [wrapper-type (class-name->wrapper-class-name (.getName clazz))]
    (reify Wrapper
      (wrap [_ v]
        `(new ~wrapper-type ~v))

      (unwrap [_ v]
        `(cond
           (= (class ~v) ~clazz) ~v
           (= (class ~v) ~wrapper-type)
           ~(let [u (with-meta (gensym 'u) {:tag 'IProtoMap})]
              `(let [~u ~v]
                 (pronto.core/get-proto ~u)))
           :else                 (throw (IllegalArgumentException. "blarg")))))))

(defmethod gen-wrapper
  :primitive
  [^Class clazz]
  (let [wrapper-type (class-name->wrapper-class-name (.getName clazz))]
    (reify Wrapper
      (wrap [_ v] v)

      (unwrap [_ v]
        `(if (= (class ~v) ~clazz)
           ~v
           (throw (IllegalArgumentException. "blarg")))))))

(defn get-field-type [^Class clazz fd]
  (let [^Method m (.getDeclaredMethod clazz (str "get" (field->camel-case fd))
                                      (make-array Class 0))]
    (.getReturnType m)))

(defn uncapitalize [s]
  (str (clojure.string/lower-case (subs s 0 1)) (subs s 1)))

(defn get-parameterized-type [parameter-index ^Class clazz ^Descriptors$FieldDescriptor fd]
  (if (message? fd)
    (let [field-name (-> fd field->camel-case uncapitalize (str "_"))
          ^Field field (.getDeclaredField clazz field-name)
          ^Type type (.getGenericType field)]
      (if (instance? ParameterizedType type)
        (aget (.getActualTypeArguments ^ParameterizedType type) parameter-index )
        (throw (UnsupportedOperationException. (str "can't infer type for " (.getName fd))))))
    (fd->java-type fd)))

(def get-map-inner-type (partial get-parameterized-type 1))

(def get-repeated-inner-type (partial get-parameterized-type 0))

(defmethod get-type-gen
  :simple
  [^Class clazz ^Descriptors$FieldDescriptor fd]
  (let [kw         (fd->field-keyword fd)
        cc         (field->camel-case fd)
        setter     (symbol (str ".set" cc))
        getter     (symbol (str ".get" cc))
        field-type (get-field-type clazz fd)
        wrapper    (gen-wrapper field-type)]
    (reify TypeGen
      (get-class [_] field-type)

      (gen-setter [_ o k v]
        (let [res (with-meta (gensym 'res) {:tag (symbol (.getName field-type))})]
          `(let [b# (.toBuilder ~o)
                 ~res ~(unwrap wrapper v)]
             (~setter b# ~res))))
      (gen-getter [_ o k]
        (let [v (gensym 'v)]
          `(let [~v (~getter ~o)]
             ~(wrap wrapper v)))))))

(defmethod get-type-gen
  :map
  [^Class clazz ^Descriptors$FieldDescriptor fd]
  (let [cc             (field->camel-case fd)
        inner-type     (get-map-inner-type clazz fd)
        wrapper        (gen-wrapper inner-type)
        clear-method   (symbol (str ".clear" cc))
        put-all-method (symbol (str ".putAll" cc))]
    (reify TypeGen

      (get-class [_] inner-type)

      (gen-setter [_ o k v]
        `(if-not (.isAssignableFrom Iterable (class ~v))
          (throw (IllegalArgumentException. (make-error-message ~k Iterable (type ~v))))
          (let [builder# (.toBuilder ~o)
                al#      (java.util.ArrayList. (count ~v))]
            (doseq [x# ~v]
              (.add al# ~(unwrap wrapper v)))
            (~put-all-method (~clear-method builder#) al#))))

      (gen-getter [_ o k]
        (let [v (with-meta (gensym 'v) {:tag 'java.util.Map})]
          `(let [~v (~(symbol (str ".get" cc "Map")) ~o)
                 al# (java.util.ArrayList. (.size ~v))]
             (doseq [x# al#]
               (.add al# ~(wrap wrapper v)))
             (clojure.lang.PersistentVector/create al#)))))))

(defmethod get-type-gen
  :one-of
  [^Class clazz ^Descriptors$FieldDescriptor fd]
  (let [kw (fd->field-keyword fd)
        cc (field->camel-case fd)
        setter (symbol (str ".set" cc))
        getter (symbol (str ".get" cc))]
    (reify TypeGen
      (get-class [_]
        String)

      (gen-setter [_ o k v]
        )
      (gen-getter [_ o k]
        ))))

(defn ^String make-error-message [key-name expected-type actual-type]
  (str "wrong value type for key " key-name ": expected " expected-type ", got " actual-type))


(defmethod get-type-gen
  :repeated
  [^Class clazz ^Descriptors$FieldDescriptor fd]
  (let [cc             (field->camel-case fd)
        inner-type     (get-repeated-inner-type clazz fd)
        wrapper        (gen-wrapper inner-type)
        clear-method   (symbol (str ".clear" cc))
        add-all-method (symbol (str ".addAll" cc))]
    (reify TypeGen

      (get-class [_] inner-type)

      (gen-setter [_ o k v]
        `(if-not (.isAssignableFrom Iterable (class ~v))
          (throw (IllegalArgumentException. (make-error-message ~k Iterable (type ~v))))
          (let [builder# (.toBuilder ~o)
                al#      (java.util.ArrayList. (count ~v))]
            (doseq [x# ~v]
              (.add al# ~(unwrap wrapper v)))
            (~add-all-method (~clear-method builder#) al#))))

      (gen-getter [_ o k]
        (let [v (with-meta (gensym 'v) {:tag 'java.util.List})]
          `(let [~v (~(symbol (str ".get" cc "List")) ~o)
                 al# (java.util.ArrayList. (.size ~v))]
             (doseq [x# al#]
               (.add al# ~(wrap wrapper v)))
             (clojure.lang.PersistentVector/create al#)))))))

(defn get-fields [^Class clazz]
  (let [class-descriptor  (descriptor clazz)
        field-descriptors (field-descriptors class-descriptor)]
    (for [fd field-descriptors]
      {:fd       fd
       :type-gen (get-type-gen clazz fd)
       :kw       (keyword (->kebab-case (.getName fd)))})))

(defn get-field-descriptors [x])

(defn resolve-deps
  ([^Class clazz] (first (resolve-deps clazz #{})))
  ([^Class clazz seen-classes]
   (let [fields       (get-fields clazz)
         deps-classes (->> fields
                           (map #(get-class (:type-gen %)))
                           (filter (complement primitive?)))
         seen-classes (conj seen-classes clazz)]
     (reduce (fn [[deps seen :as acc] dep-class]
               (if (get seen dep-class)
                 acc
                 (let [new-deps  (conj deps dep-class)
                       [x y] (resolve-deps dep-class seen-classes)]
                   [(into new-deps x) y])))
             [[] seen-classes]
             deps-classes))))


(defn static-call [^Class class method-name]
  (symbol (str (.getName class) "/" method-name)))


(defprotocol IProtoMap
  (get-proto [this]))

(defn get-builder-class [^Class clazz]
  (.getReturnType (.getDeclaredMethod clazz "toBuilder" (make-array Class 0))))

(defmacro defproto [class-sym]
  (let [^Class clazz       (cond
                             (class? class-sym)  class-sym
                             (symbol? class-sym) (resolve class-sym)
                             :else               (throw (IllegalArgumentException. (str "defproto: expected a class or class symbol, got " (class class-sym)))))
        descriptor         (descriptor clazz)
        deps               (reverse (flatten (resolve-deps clazz)))
        fields             (get-fields clazz)
        o                  (with-meta (gensym 'o) {:tag (symbol (.getName clazz))})
        builder-class      (get-builder-class clazz)
        wrapper-class-name (class-name->wrapper-class-name (.getName clazz))]
    `(do
       ~@(for [dep deps]
           `(defproto ~dep))

       (deftype ~wrapper-class-name [~o]

         pronto.core.IProtoMap 

         (~'get-proto [this#] ~o)

         Associative

         ~(let [this (gensym 'this)
                k    (gensym 'k)
                v    (gensym 'v)
                b    (with-meta (gensym 'builder) {:tag (symbol (.getName builder-class))})]
            `(~'assoc [~this ~k ~v]
              (let [~b (case ~k
                         ~@(interleave
                            (map :kw fields)
                            (map #(gen-setter (:type-gen %) o k v) fields))
                         

                         (throw (IllegalArgumentException. (str "cannot assoc " ~k))))]
                (new ~wrapper-class-name (.build ~b)))))


         (containsKey [this# k#]
           (boolean (get ~(into #{} (map :kw fields))
                         k#)))

         (entryAt [this# k#]
           (when-let [v# (.valAt this# k#)]
             (MapEntry/create k# v#)))

         MapEquivalence
         IPersistentMap

         (without [this# k#]
           (throw (UnsupportedOperationException. "cannot dissoc from a proto map")))

         ILookup

         ~(let [k (gensym 'k)]
            `(valAt [this# ~k]
                   (case ~k

                     ~@(interleave
                        (map :kw fields)
                        (map #(gen-getter (:type-gen %) o k) fields))

                     nil)))

         (valAt [this# k# not-found#]
           (.valAt this# k#))


         IPersistentCollection

         (cons [this# o#] (throw (Exception. "not implemented yet")))

         (empty [this#]
           (new ~wrapper-class-name
                (.build (~(static-call clazz "newBuilder")))))

         (count [this#] ~(count fields))

         (equiv [this# other#]
           (and (map? other#)
                ))

         Seqable

         ~(let [this (gensym 'this)]
            `(seq [~this]
                  (ArraySeq/create
                   (object-array
                    [~@(map (fn [fd]
                              `(.entryAt ~this ~(:kw fd)))
                            fields)]))))


         java.lang.Iterable

         ~(let [this (gensym 'this)]
            `(iterator [~this]
                       (ArrayIter/create
                        (object-array
                         [~@(map (fn [fd]
                                   `(.entryAt ~this ~(:kw fd)))
                                 fields)]))))
         ))))
