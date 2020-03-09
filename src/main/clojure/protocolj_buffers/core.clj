(ns pronto.core
  (:import [clojure.lang Reflector Associative APersistentMap ILookup Counted IPersistentMap
            MapEntry IPersistentCollection MapEquivalence Seqable ArrayIter ArraySeq]
           [protogen.generated People$Person People$Person$Builder
            People$Address People$Address$Builder]
           [com.google.protobuf Descriptors 
            Descriptors$Descriptor
            Descriptors$FieldDescriptor
            Descriptors$FieldDescriptor$Type
            Descriptors$FieldDescriptor$JavaType]))


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


(defn fd->java-type [^Descriptors$FieldDescriptor fd]
  (condp = (.getJavaType fd)
    Descriptors$FieldDescriptor$JavaType/INT Integer
    Descriptors$FieldDescriptor$JavaType/STRING String
    Object))

(def primitive? (comp boolean #{Integer} fd->java-type))

(defn message? [^Descriptors$FieldDescriptor fd]
  (= (.getType fd)
     Descriptors$FieldDescriptor$Type/MESSAGE))

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

(defn get-field-descriptors [^Class clazz]
  (let [class-descriptor  (descriptor clazz)
        field-descriptors (field-descriptors class-descriptor)]
    (for [fd   field-descriptors
          :let [field-name (fd->field-keyword fd)
                cc (field->camel-case fd)
                setter (symbol (str ".set" cc))
                getter (symbol (str ".get" cc))
                generated-class (->> clazz
                                     (.getMethods)
                                     (filter #(= (.getName %) (str "get" cc)))
                                     first
                                     (.getReturnType))
                repeated? (.isRepeated fd)]]
      {:setter          setter
       :getter          (symbol (str ".get" cc (when repeated? "List")))
       :generated-class generated-class
       :wrapper-class   (when (message? fd)
                          (descriptor->deftype-class (.getMessageType fd)))
       :kw              (fd->field-keyword fd)
       :repeated?       repeated?
       :fd              fd})))

(defn generate-get [fd o k v]
  (cond
    (:repeated? fd)
    `(into []
           (map (fn [x#] (new ~(:wrapper-class fd) x#)))
           (~(:getter fd) ~o))
    :else `(~(:getter fd) ~o)))

(defn resolve-deps
  ([^Class clazz] (first (resolve-deps clazz #{})))
  ([^Class clazz seen-classes]
   (let [fields (get-field-descriptors clazz)
         deps-classes (->> fields
                           (filter #(message? (:fd %)))
                           (map :generated-class))
         seen-classes (conj seen-classes clazz)]
     (reduce (fn [[deps seen :as acc] dep-class]
               (if (get seen dep-class)
                 acc
                 (let [new-deps (conj deps dep-class)
                       [x y] (resolve-deps dep-class seen-classes)]
                   [(into new-deps x) y])))
             [[] seen-classes]
             deps-classes))))


(defn static-call [^Class class method-name]
  (symbol (str (.getName class) "/" method-name)))


(defmacro defproto [class-sym]
  (let [^Class clazz       (cond
                             (class? class-sym)  class-sym
                             (symbol? class-sym) (resolve class-sym)
                             :else               (throw (IllegalArgumentException. (str "defproto: expected a class or class symbol, got " (class class-sym)))))
        descriptor         (descriptor clazz)
        deps               (reverse (flatten (resolve-deps clazz)))
        fields             (get-field-descriptors clazz)
        fn-name            'make-foo #_ (symbol (str "make-" class-sym))
        o                  (with-meta (gensym 'o) {:tag (symbol (.getName clazz))})
        wrapper-class-name (descriptor->deftype-class descriptor)]
    `(do
       ~@(for [dep deps]
           `(defproto ~dep))

       (deftype ~wrapper-class-name [~o]

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






