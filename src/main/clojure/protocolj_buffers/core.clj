(ns pronto.core
  (:import [clojure.lang Reflector Associative APersistentMap ILookup Counted IPersistentMap
            MapEntry IPersistentCollection MapEquivalence Seqable ArrayIter ArraySeq]
           [protogen.generated People$Person People$Person$Builder
            People$Address People$Address$Builder People$Like People$Level
            People$House People$Apartment]
           [com.google.protobuf Descriptors 
            Descriptors$Descriptor
            Descriptors$FieldDescriptor
            Descriptors$FieldDescriptor$Type
            Descriptors$FieldDescriptor$JavaType
            ByteString]
           [java.lang.reflect Field Method ParameterizedType]
           [java.util Map]))

(defprotocol IProtoMap
  (get-proto [this]))

(defn descriptor [^Class clazz]
  (Reflector/invokeStaticMethod clazz "getDescriptor" (to-array nil)))

(defn field-descriptors [^Descriptors$Descriptor descriptor]
  (.getFields descriptor))

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

(defn clojurify-descriptor-name [^Descriptors$Descriptor descriptor]
  (clojure.string/replace (.getFullName descriptor) "." "-"))

(defn class-name->wrapper-class-name [class-name]
  (symbol (str 'wrapped- (clojure.string/replace class-name "." "-"))))

(defn fd->wrapper-class-name [^Descriptors$FieldDescriptor fd]
  (if (message? fd)
    (class-name->wrapper-class-name (.getFullName fd))
    (throw (IllegalArgumentException. (str "no wrapper type for " (.getType fd))))))

(def primitive?
  (comp boolean
        #{Integer/TYPE Integer
          Long/TYPE      Long
          Double/TYPE    Double
          Float/TYPE     Float
          Boolean/TYPE   Boolean}))

(def numeric-scalar?
  (comp boolean #{Integer/TYPE Long/TYPE Double/TYPE Float/TYPE}))

(defn protobuf-scalar? [^Class clazz]
  (boolean (or (numeric-scalar? clazz)
               (#{Boolean/TYPE String ByteString} clazz))))


(defn with-type-hint [sym ^Class clazz]
  (with-meta sym {:tag (symbol (.getName clazz))}))

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

(defn ^String make-error-message [key-name expected-type actual-type]
  (str "wrong value type for key " key-name ": expected " expected-type ", got " actual-type))

(defprotocol TypeGen
  (get-class [this])
  (gen-setter [this builder k v])
  (gen-getter [this o k]))

(defprotocol Wrapper
  (wrap [this v])
  (unwrap [this v]))

(defmulti gen-wrapper
  (fn [^Class clazz]
    (cond
      (.isEnum clazz) :enum
      (= ByteString clazz) :bytes
      (not (protobuf-scalar? clazz)) :message
      :else :scalar)))

(defmethod gen-wrapper
  :enum
  [^Class clazz]
  (let [values (Reflector/invokeStaticMethod clazz "values" (to-array nil))
        enum->kw (map-indexed #(vector %1 (keyword (->kebab-case (.name %2))))
                              values)
        kw->enum (map #(vector (keyword (->kebab-case (.name %1)))
                               (symbol (str (.getName clazz) "/" (.name %1))))
                      values)]
    
    (reify Wrapper
      (wrap [_ v]
        `(case (.ordinal ~v)
           ~@(interleave
             (map first enum->kw)
             (map second enum->kw))
           (throw (IllegalArgumentException. (str "can't wrap " ~v)))))

      (unwrap [_ v]
        `(case ~v
            ~@(interleave
               (map first kw->enum)
               (map second kw->enum))
            (throw (IllegalArgumentException. (str "can't unwrap " ~v))))))))

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
  :bytes
  [_]
  ;; the class must be `ByteString`
  (reify Wrapper
    (wrap [_ v]
      `(pronto.ByteStringColl. ~v))

    (unwrap [_ v]
      `(cond
         (instance? pronto.ByteStringWrapper ~v)
         (.getByteString ~(with-type-hint v pronto.ByteStringWrapper))
                                        ;(coll? ~v)
         ;; TODO: what about other types of sequences?
         :else (UnsupportedOperationException. (str "can't unwrap a " ~(class v)))))))

(defmethod gen-wrapper
  :scalar
  [^Class clazz]
  (let [wrapper-type (class-name->wrapper-class-name (.getName clazz))]
    (reify Wrapper
      (wrap [_ v] v)

      (unwrap [_ v]
        (if-not (numeric-scalar? clazz)
           v
           (let [vn (with-type-hint v Number)]
             `(let [~vn ~v]
                (~(symbol (str "." (str clazz) "Value")) ~vn))))))))

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

(def get-repeated-inner-type (partial get-parameterized-type 0))

(defn get-simple-type-gen [^Class clazz ^Descriptors$FieldDescriptor fd]
  (let [kw         (fd->field-keyword fd)
        cc         (field->camel-case fd)
        setter     (symbol (str ".set" cc))
        getter     (symbol (str ".get" cc))
        field-type (get-field-type clazz fd)
        wrapper    (gen-wrapper field-type)]
    (reify TypeGen
      (get-class [_] field-type)

      (gen-setter [_ builder k v]
        (let [res (gensym 'res)
              ;; if field is primitive, don't add any additional type info
              ;; as it is already hinted
              ;; TODO: should we delegate all type hinting to wrappers?
              res (if (primitive? field-type)
                    res
                    (with-type-hint (gensym 'res) field-type))]
          `(let [~res ~(unwrap wrapper v)]
             (~setter ~builder ~res))))

      (gen-getter [_ o k]
        (let [v (gensym 'v)]
          `(let [~v (~getter ~o)]
             ~(wrap wrapper v)))))))

(defmethod get-type-gen
  :simple
  [^Class clazz ^Descriptors$FieldDescriptor fd]
  (get-simple-type-gen clazz fd))

(defmethod get-type-gen
  :map
  [^Class clazz ^Descriptors$FieldDescriptor fd]
  (let [cc           (field->camel-case fd)
        key-type     (get-parameterized-type 0 clazz fd)
        val-type     (get-parameterized-type 1 clazz fd)
        key-wrapper  (gen-wrapper key-type)
        val-wrapper  (gen-wrapper val-type)
        clear-method (symbol (str ".clear" cc))
        put-method   (symbol (str ".put" cc))
        entry-key    (gensym 'entry-key)
        entry-val    (gensym 'entry-val)]
    (reify TypeGen

      (get-class [_] val-type)

      (gen-setter [_ builder k v]
        (let [m (with-type-hint (gensym 'm) java.util.Map)]
          `(if-not (.isAssignableFrom java.util.Map (class ~v))
             (throw (IllegalArgumentException. (make-error-message ~k java.util.Map (type ~v))))
             (let [~m       ~v
                   ~builder (~clear-method ~builder)]
               (doseq [x# (.entrySet ~v)]
                 (let [~entry-key (.getKey x#)
                       ~entry-val (.getValue x#)]
                   (~put-method ~builder
                    ~(unwrap key-wrapper entry-key)
                    ~(unwrap key-wrapper entry-val))))))))

      (gen-getter [_ o k]
        (let [v (with-meta (gensym 'v) {:tag 'java.util.Map})]
          `(let [~v       (~(symbol (str ".get" cc "Map")) ~o)
                 new-map# (java.util.HashMap. (.size ~v))]
             (doseq [x# (.entrySet ~v)]
               (let [~entry-key   (.getKey x#)
                     ~entry-val   (.getValue x#)
                     wrapped-key# ~(wrap key-wrapper entry-key)]
                 (.put new-map#
                       (if (string? wrapped-key#)
                         (keyword wrapped-key#)
                         wrapped-key#)
                       ~(wrap val-wrapper entry-val))))
             (clojure.lang.PersistentHashMap/create new-map#)))))))

(defmethod get-type-gen
  :one-of
  [^Class clazz ^Descriptors$FieldDescriptor fd]
  (let [g                (get-simple-type-gen clazz fd)
        containing-oneof (.getContainingOneof fd)
        cc               (field->camel-case containing-oneof)
        case-enum-getter (symbol (str ".get" cc "Case"))
        field-num        (.getNumber fd)]
    (reify TypeGen
      (get-class [_] (get-class g))

      (gen-setter [_ builder k v] (gen-setter g builder k v))

      (gen-getter [_ o k] 
        `(when (= ~field-num (.getNumber (~case-enum-getter ~o)))
           ~(gen-getter g o k))))))


(defmethod get-type-gen
  :repeated
  [^Class clazz ^Descriptors$FieldDescriptor fd]
  (let [cc             (field->camel-case fd)
        inner-type     (get-parameterized-type 0 clazz fd)
        wrapper        (gen-wrapper inner-type)
        clear-method   (symbol (str ".clear" cc))
        add-all-method (symbol (str ".addAll" cc))
        x (gensym 'x)]
    (reify TypeGen

      (get-class [_] inner-type)

      (gen-setter [_ builder k v]
        `(if-not (.isAssignableFrom Iterable (class ~v))
          (throw (IllegalArgumentException. (make-error-message ~k Iterable (type ~v))))
          (let [al#      (java.util.ArrayList. (count ~v))]
            (doseq [~x ~v]
              (.add al# ~(unwrap wrapper x)))
            (~add-all-method (~clear-method ~builder) al#))))

      (gen-getter [_ o k]
        (let [v (with-meta (gensym 'v) {:tag 'java.util.List})]
          `(let [~v (~(symbol (str ".get" cc "List")) ~o)
                 al# (java.util.ArrayList. (.size ~v))]
             (doseq [~x ~v]
               (.add al# ~(wrap wrapper x)))
             (clojure.lang.PersistentVector/create al#)))))))

(defn get-fields [^Class clazz]
  (let [class-descriptor  (descriptor clazz)
        field-descriptors (field-descriptors class-descriptor)]
    (for [fd field-descriptors]
      {:fd       fd
       :type-gen (get-type-gen clazz fd)
       :kw       (keyword (->kebab-case (.getName fd)))})))

(defn resolve-deps
  ([^Class clazz] (first (resolve-deps clazz #{})))
  ([^Class clazz seen-classes]
   (let [fields       (get-fields clazz)
         deps-classes (->> fields
                           (map #(get-class (:type-gen %)))
                           (filter (fn [^Class clazz]
                                     (and (not (.isEnum clazz))
                                          (not (protobuf-scalar? clazz))))))
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


(defn get-builder-class [^Class clazz]
  (.getReturnType (.getDeclaredMethod clazz "toBuilder" (make-array Class 0))))


(def loaded-classes (atom #{}))

(defmacro defproto [class-sym]
  (let [^Class clazz       (cond
                             (class? class-sym)  class-sym
                             (symbol? class-sym) (resolve class-sym)
                             :else               (throw (IllegalArgumentException. (str "defproto: expected a class or class symbol, got " (class class-sym)))))
        descriptor         (descriptor clazz)
        deps               (reverse (flatten (resolve-deps clazz)))
        fields             (get-fields clazz)
        o                  (with-type-hint (gensym 'o) clazz)
        builder-class      (get-builder-class clazz)
        wrapper-class-name (class-name->wrapper-class-name (.getName clazz))
        deftype-ctor-name  (symbol (str '-> wrapper-class-name))
        ;; TODO: refactor duplicated code
        ctor-name          (symbol (str 'proto-> (clojure.string/replace (.getName clazz) "." "-")))
        class-key          [*ns* clazz]]
    (locking loaded-classes
      (when (not (get @loaded-classes class-key))
        (swap! loaded-classes conj class-key)
        `(do
           ~@(for [dep deps]
               `(defproto ~dep))

           (deftype ~wrapper-class-name [~o]

             java.io.Serializable

             pronto.core.IProtoMap 

             (~'get-proto [this#] ~o)

             Associative

             ~(let [this (gensym 'this)
                    k    (gensym 'k)
                    v    (gensym 'v)
                    b    (with-type-hint (gensym 'builder) builder-class)]
                `(~'assoc [~this ~k ~v]
                  (let [~b (.toBuilder ~o)]
                    (case ~k
                      ~@(interleave
                         (map :kw fields)
                         (map #(gen-setter (:type-gen %) b k v) fields))
                      (throw (IllegalArgumentException. (str "cannot assoc " ~k))))
                    (new ~wrapper-class-name (.build ~b)))))


             (containsKey [this# k#]
               (boolean (get ~(into #{} (map :kw fields))
                             k#)))

             (entryAt [this# k#]
               (MapEntry/create k# (.valAt this# k#)))

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

             (cons [this# o#]
               (pronto.PersistentMapHelpers/cons this# o#))

             (empty [this#]
               (new ~wrapper-class-name
                    (.build (~(static-call clazz "newBuilder")))))

             (count [this#] ~(count fields))

             (equiv [this# other#]
               (pronto.PersistentMapHelpers/equiv this# other#))

             Seqable

             ~(let [this (gensym 'this)]
                `(seq [~this]
                      (ArraySeq/create
                       (object-array
                        [~@(map (fn [fd]
                                  `(.entryAt ~this ~(:kw fd)))
                                fields)]))))

             pronto.DefaultingFn

             (invoke [this# arg1#]
               (pronto.PersistentMapHelpers/invoke this# arg1#))

             (invoke [this# arg1# not-found#]
               (pronto.PersistentMapHelpers/invoke this# arg1# not-found#))


             java.lang.Iterable

             ~(let [this (gensym 'this)]
                `(iterator [~this]
                           (ArrayIter/create
                            (object-array
                             [~@(map (fn [fd]
                                       `(.entryAt ~this ~(:kw fd)))
                                     fields)]))))

             java.util.Map

             (clear [this#] (throw (UnsupportedOperationException.)))

             (containsValue [this# value#]
               (pronto.PersistentMapHelpers/containsValue this# value#))

             (entrySet [this#]
               (pronto.PersistentMapHelpers/entrySet this#))

             (keySet [this#]
               (pronto.PersistentMapHelpers/keySet this#))

             (values [this#]
               (pronto.PersistentMapHelpers/values this#))

             (get [this# key#]
               (pronto.PersistentMapHelpers/get this# key#))

             (isEmpty [this#]
               (pronto.PersistentMapHelpers/isEmpty this#))

             (put [this# k# v#] (throw (UnsupportedOperationException.)))

             (putAll [this# m#] (throw (UnsupportedOperationException.)))

             (remove [this# k#] (throw (UnsupportedOperationException.)))

             (size [this#] (.count this#))

             Object

             (toString [this#]
               (pronto.PersistentMapHelpers/toString this#))

             (equals [this# obj#]
               (pronto.PersistentMapHelpers/equals this# obj#)))

           (def ~ctor-name
             (fn [o#]
               (cond
                 (instance? ~clazz o#) (new ~wrapper-class-name o#)
                 (map? o#)
                 (let [res# (new ~wrapper-class-name (.build (~(static-call clazz "newBuilder"))))]
                   (reduce (fn [acc# [k# v#]]
                             (assoc res# k# v#))
                           res#
                           o#))
                 :else
                 (throw (IllegalArgumentException. (str "cannot wrap " (class o#))))))))))))


(defn make-house [& {:keys [num-rooms]}]
  (cond-> (People$House/newBuilder)
    num-rooms (.setNumRooms num-rooms)
    true (.build)))

(defn make-apartment [& {:keys [floor-num]}]
  (cond-> (People$Apartment/newBuilder)
    floor-num (.setFloorNum floor-num)
    true (.build)))

(defn make-like [& {:keys [desc level]}]
  (cond-> (People$Like/newBuilder)
    desc (.setDesc desc)
    level (.setLevel level)
    true (.build)))

(defn make-address
  ([] (make-address :city "fooville" :street "broadway" :house-num 21213))
  ([& {:keys [city street house-num]}]
   (cond-> (People$Address/newBuilder)
     city (.setCity city)
     street (.setStreet street)
     house-num (.setHouseNum house-num)
     true (.build))))

(defn make-person
  ([] (make-person :id 5 :name "Foo"
                   :email "foo@bar.com"
                   :address (make-address)
                   :pet-names ["bla" "booga"]
                   :likes
                   [(make-like "low" People$Level/LOW)
                    (make-like "medium" People$Level/MEDIUM)
                    (make-like "high" People$Level/HIGH)]))
  ([& {:keys [id name email address likes pet-names relations]}]
   (cond-> (People$Person/newBuilder)
     id (.setId id)
     name (.setName name)
     email (.setEmail email)
     address (.setAddress address)
     likes (.addAllLikes likes)
     pet-names (.addAllPetNames pet-names)
     relations (.putAllRelations relations)
     true (.build))))


(defproto People$Address)
