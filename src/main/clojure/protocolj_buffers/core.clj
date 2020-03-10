(ns pronto.core
  (:import [clojure.lang Reflector Associative APersistentMap ILookup Counted IPersistentMap
            MapEntry IPersistentCollection MapEquivalence Seqable ArrayIter ArraySeq]
           [protogen.generated People$Person People$Person$Builder
            People$Address People$Address$Builder]
           [com.google.protobuf Descriptors 
            Descriptors$Descriptor
            Descriptors$FieldDescriptor
            Descriptors$FieldDescriptor$Type
            Descriptors$FieldDescriptor$JavaType]
           [java.lang.reflect Field ParameterizedType]))


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

(def primitive? (comp boolean #{Integer String}))

(defn dispatch-fd [^Descriptors$FieldDescriptor fd]
  {:repeated?  (.isRepeated fd)
   :primitive? (primitive? fd)
   :message?   (message? fd)})

(defmulti generate-setter*
  (fn [fd o k v]
    (dispatch-fd (:fd fd))))

(defmethod generate-setter*
  {:repeated? false
   :primitive? true
   :message? false}
  [fd o k v]
  (let [kw (:kw fd)
        setter (:setter fd)]
    `(if (or (nil? ~v)
             (not= (class ~v) ~(fd->java-type (:fd fd))))
       (throw (IllegalArgumentException. (str "wrong type for" ~k)))
       (let [b# (.toBuilder ~o)]
         (~setter b# ~v)))))

(defmethod generate-setter*
  {:repeated? false
   :primitive? false
   :message? false}
  [fd o k v]
  (let [kw (:kw fd)
        setter (:setter fd)]
    `(if (or (nil? ~v)
             (not= (class ~v) ~(fd->java-type (:fd fd))))
       (throw (IllegalArgumentException. (str "wrong type for " ~k)))
       (let [b# (.toBuilder ~o)]
         (~setter b# ~v)
         ))))

(defmacro cond* [& body]
  `(cond
     ~@(interleave
        (map first body)
        (map second body))))

(defmethod generate-setter*
  {:repeated?  false
   :primitive? false
   :message?   true}
  [fd o k v]

  (let [val (with-meta (gensym 'val) {:tag (:generated-class fd)})]
    `(let [~val (cond
                  (= (class ~v) ~(:generated-class fd))
                  ~v

                  (= (class ~v) ~(:wrapper-class fd))
                  (.getProto ~v))]
       (-> ~o
         (.toBuilder)
         (~(:setter fd) ~val)))))

(defmethod generate-setter*
  {:repeated? true
   :primitive? false
   :message? true}
  [fd o k v]
  )

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
      (not (primitive? clazz)) :message
      :else :primitive)))

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
           (let [u# ~(with-meta (gensym 'u) {:tag 'IProtoMap})]
             (.getProto u#))
           :else (throw (IllegalArgumentException. "blarg")))))))

(defn get-field-type [^Class clazz field-name]
  (let [^Field field (.getDeclaredField clazz (str field-name "_"))]
    (.getType field)))

(defmethod get-type-gen
  :simple
  [^Class clazz ^Descriptors$FieldDescriptor fd]
  (let [kw (fd->field-keyword fd)
        cc (field->camel-case fd)
        setter (symbol (str ".set" cc))
        getter (symbol (str ".get" cc))]
    (reify TypeGen
      (get-class [_]
        (get-field-type clazz (.getName fd)))

      (gen-setter [_ o k v]
        `(let [b# (.toBuilder ~o)]
           (~setter b# ~v)))
      (gen-getter [_ o k]
        ))))

(defmethod get-type-gen
  :map
  [^Class clazz ^Descriptors$FieldDescriptor fd]
  (let [kw (fd->field-keyword fd)
        cc (field->camel-case fd)
        setter (symbol (str ".set" cc))
        getter (symbol (str ".get" cc))]
    (reify TypeGen
      (get-class [_]
        )

      (gen-setter [_ o k v]
        )
      (gen-getter [_ o k]
        ))))

(defn make-error-message [key-name expected-type actual-type]
  (str "wrong value type for key " key-name ": expected " expected-type ", got " actual-type))


#_(defn get-types [^Descriptors$FieldDescriptor fd]
  [(.getType fd)
   (if (message? fd)
     (Class/forName (.getFullName (.getMessageType fd)))
     (fd->java-type))])

(defn uncapitalize [s]
  (str (clojure.string/lower-case (subs s 0 1)) (subs s 1)))

(defn get-repeated-inner-type [^Class clazz ^Descriptors$FieldDescriptor fd]
  (let [field-name (-> fd field->camel-case uncapitalize (str "_"))
        ^Field field (.getDeclaredField clazz field-name)
        ^Type type (.getGenericType field)]
    (if (instance? ParameterizedType type)
      (aget (.getActualTypeArguments ^ParameterizedType type) 0)
      type)))

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
                al#      (ArrayList. (count ~v))]
            (doseq [x# ~v]
              (.add al ~(unwrap wrapper v)))
            (~add-all-method (~clear-method builder#) al#))))

      (gen-getter [_ o k]
        (let [v (with-meta (gensym 'v) {:tag (symbol 'java.util.List)})]
          `(let [~v (~(str cc "List") ~o)
                 al# (ArrayList. (.size ~v))]
             (doseq [x# al#]
               (.add al# ~(wrap wrapper v)))
             (PersistentVector/create al#)))))))

;; (defmethod get-type-gen
;;   {:repeated? false
;;    :primitive? true
;;    :message? false}
;;   [fd o k v]
;;   (let [kw (:kw fd)
;;         setter (:setter fd)]
;;     `(if (or (nil? ~v)
;;              (not= (class ~v) ~(fd->java-type (:fd fd))))
;;        (throw (IllegalArgumentException. (str "wrong type for" ~k)))
;;        (let [b# (.toBuilder ~o)]
;;          (~setter b# ~v)))))

;; (defmethod get-type-gen
;;   {:repeated? false
;;    :primitive? false
;;    :message? false}
;;   [fd o k v]
;;   (let [kw (:kw fd)
;;         setter (:setter fd)]
;;     `(if (or (nil? ~v)
;;              (not= (class ~v) ~(fd->java-type (:fd fd))))
;;        (throw (IllegalArgumentException. (str "wrong type for " ~k)))
;;        (let [b# (.toBuilder ~o)]
;;          (~setter b# ~v)
;;          ))))


(defn get-fields [^Class clazz]
  (let [class-descriptor  (descriptor clazz)
        field-descriptors (field-descriptors class-descriptor)]
    (for [fd field-descriptors]
      {:fd fd :type-gen (get-type-gen clazz fd)})))

(defn generate-get [fd o k v]
  (cond
    (:repeated? fd)
    `(into []
           (map (fn [x#] (new ~(:wrapper-class fd) x#)))
           (~(:getter fd) ~o))
    :else `(~(:getter fd) ~o)))

(defn get-field-descriptors [x])

(defn resolve-deps
  ([^Class clazz] (first (resolve-deps clazz #{})))
  ([^Class clazz seen-classes]
   (let [fields       (get-field clazz)
         deps-classes (->> fields
                           (filter #(message? (:fd %)))
                           (map :generated-class))
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


(definterface IProtoMap
  (^Object getProto
   "Returns the underlying protobuf object contained by the map"
   []))

(defmacro defproto [class-sym]
  (let [^Class clazz       (cond
                             (class? class-sym)  class-sym
                             (symbol? class-sym) (resolve class-sym)
                             :else               (throw (IllegalArgumentException. (str "defproto: expected a class or class symbol, got " (class class-sym)))))
        descriptor         (descriptor clazz)
        deps               (reverse (flatten (resolve-deps clazz)))
        fields             (get-fields clazz)
        o                  (with-meta (gensym 'o) {:tag (symbol (.getName clazz))})
        wrapper-class-name (descriptor->deftype-class descriptor)]
    `(do
       ~@(for [dep deps]
           `(defproto ~dep))

       (deftype ~wrapper-class-name [~o]

         IProtoMap

         (getProto [this#] ~o)

         Associative

         ~(let [this (gensym 'this)
                k    (gensym 'k)
                v    (gensym 'v)]
            `(~'assoc [~this ~k ~v]
              (let [b# (case ~k
                         ~@(interleave
                            (map :kw fields)
                            (map #(generate-setter* % o k v) fields))

                         (throw (IllegalArgumentException. (str "cannot assoc " ~k))))]
                (new ~wrapper-class-name (.build b#)))))


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

         (valAt [this# k#]
           (case k#
             ~@(interleave
                (map :kw fields)
                (map (fn [field] (generate-get field o nil nil)) fields))

             nil))

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






