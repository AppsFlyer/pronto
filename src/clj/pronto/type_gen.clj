(ns pronto.type-gen
  (:require
   [clojure.string :as s]
   [pronto.wrapper :as w]
   [pronto.utils :as u])
  (:import
   [clojure.lang Reflector]
   [com.google.protobuf
    Descriptors$Descriptor
    Descriptors$FieldDescriptor
    Descriptors$FieldDescriptor$Type
    Descriptors$FieldDescriptor$JavaType]
   [java.lang.reflect Type Method ParameterizedType]
   [java.util Map$Entry]))


(defprotocol TypeGen
  (get-class [this])
  (gen-setter [this builder v])
  (gen-getter [this o]))

(defn descriptor [^Class clazz]
  (Reflector/invokeStaticMethod clazz "getDescriptor" (to-array nil)))

(defn field-descriptors [^Descriptors$Descriptor descriptor]
  (.getFields descriptor))

(def primitive?
  (comp boolean
        #{Integer/TYPE Integer
          Long/TYPE      Long
          Double/TYPE    Double
          Float/TYPE     Float
          Boolean/TYPE   Boolean}))

(defn fd->field-keyword [^Descriptors$FieldDescriptor fd]
  (keyword (.getName fd)))


(defn field-type [^Class clazz fd]
  (let [^Method m (.getDeclaredMethod clazz (str "get" (u/field->camel-case fd))
                                      (make-array Class 0))]
    (.getReturnType m)))


;; TODO: split this into a generator for message types and scalar types
(defn get-simple-type-gen [^Class clazz ^Descriptors$FieldDescriptor fd ctx]
  (let [cc         (u/field->camel-case fd)
        setter     (symbol (str ".set" cc))
        getter     (symbol (str ".get" cc))
        field-type (field-type clazz fd)
        wrapper    (w/gen-wrapper field-type ctx)]
    (reify TypeGen
      (get-class [_] field-type)

      (gen-setter [_ builder v]
        (let [res          (gensym 'res)
              ;; if field is primitive, don't add any additional type info
              ;; as it is already hinted
              ;; TODO: should we delegate all type hinting to wrappers?
              res          (if (primitive? field-type)
                             res
                             (u/with-type-hint (gensym 'res) field-type))
              clear-method (symbol (str ".clear" (u/field->camel-case fd)))]
          `(if (nil? ~v)
             ~(if (u/message? fd)
                `(~clear-method ~builder)
                `(throw (u/make-type-error ~clazz ~(.getName fd) ~field-type nil)))
             (let [~res ~(w/unwrap wrapper v)]
               (~setter ~builder ~res)))))

      (gen-getter [_ o]
        (let [v          (gensym 'v)
              has-method (symbol (str ".has" (u/field->camel-case fd)))
              get-form   `(let [~v (~getter ~o)]
                            ~(w/wrap wrapper v))]
          (if-not (u/message? fd)
            get-form
            `(when (~has-method ~o)
               ~get-form)))))))

(defn descriptor-type [^Descriptors$FieldDescriptor fd]
  (cond
    (.isMapField fd) :map
    (.isRepeated fd) :repeated
                                        ;(.getContainingOneof fd) :one-of
    :else            :simple))

(defmulti get-type-gen
  (fn [^Class _clazz
       ^Descriptors$FieldDescriptor$Type fd
       _ctx]
    (descriptor-type fd)))

(defmethod get-type-gen
  :simple
  [^Class clazz ^Descriptors$FieldDescriptor fd ctx]
  (get-simple-type-gen clazz fd ctx))

(defn uncapitalize [s]
  (str (s/lower-case (subs s 0 1)) (subs s 1)))

(defn find-type [^Class clazz ^Descriptors$FieldDescriptor fd]
  (.getGenericReturnType (.getDeclaredMethod clazz (str "get" (u/field->camel-case fd)
                                                        (if (.isMapField fd)
                                                          "Map"
                                                          (when (.isRepeated fd)
                                                            "List")))
                                             (make-array Class 0))))

(defn fd->java-type [^Descriptors$FieldDescriptor fd]
  (if (u/message? fd)
    (Class/forName (.getFullName (.getMessageType fd)))
    (condp = (.getJavaType fd)
      Descriptors$FieldDescriptor$JavaType/INT    Integer
      Descriptors$FieldDescriptor$JavaType/STRING String)))

(defn get-parameterized-type [parameter-index ^Class clazz ^Descriptors$FieldDescriptor fd]
  (if (or (u/message? fd) (u/enum? fd))
    (let [^Type type (find-type clazz fd)]
      (if (instance? ParameterizedType type)
        (aget (.getActualTypeArguments ^ParameterizedType type) parameter-index)
        (throw (UnsupportedOperationException. (str "can't infer type for " (.getName fd))))))
    (fd->java-type fd)))

(def get-repeated-inner-type (partial get-parameterized-type 0))

(defmethod get-type-gen
  :map
  [^Class clazz ^Descriptors$FieldDescriptor fd ctx]
  (let [cc          (u/field->camel-case fd)
        key-type    (get-parameterized-type 0 clazz fd)
        val-type    (get-parameterized-type 1 clazz fd)
        key-wrapper (if (= String key-type)
                      (reify w/Wrapper
                        (wrap [_ v]
                          ;; -> keyword
                          `(keyword ~v))

                        (unwrap [_ v]
                          ;; -> string
                          `(name ~v)))
                      (w/gen-wrapper key-type ctx))
        val-wrapper  (w/gen-wrapper val-type ctx)
        clear-method (symbol (str ".clear" cc))
        put-method   (symbol (str ".put" cc))
        m            (u/with-type-hint (gensym 'm) java.util.Map)
        entry        (u/with-type-hint (gensym 'entry) Map$Entry)
        entry-key    (gensym 'entry-key)
        entry-val    (gensym 'entry-val)]
    (reify TypeGen

      (get-class [_] val-type)

      (gen-setter [_ builder v]
        `(if-not (and (some? ~v) (.isAssignableFrom java.util.Map (class ~v)))
           (throw (u/make-type-error ~clazz ~(.getName fd) java.util.Map ~v))
           (let [~m       ~v
                 ~builder (~clear-method ~builder)]
             (doseq [~entry (.entrySet ~m)]
               (let [~entry-key (.getKey ~entry)
                     ~entry-val (.getValue ~entry)]
                 (~put-method ~builder
                  ~(w/unwrap key-wrapper entry-key)
                  ~(w/unwrap val-wrapper entry-val)))))))

      (gen-getter [_ o]
        `(let [~m       (~(symbol (str ".get" cc "Map")) ~o)
               new-map# (java.util.HashMap. (.size ~m))]
           (doseq [~entry (.entrySet ~m)]
             (let [~entry-key   (.getKey ~entry)
                   ~entry-val   (.getValue ~entry )
                   wrapped-key# ~(w/wrap key-wrapper entry-key)]
               (.put new-map#
                     wrapped-key#
                     ~(w/wrap val-wrapper entry-val))))
           (clojure.lang.PersistentHashMap/create new-map#))))))

#_(defmethod get-type-gen
    :one-of
    [^Class clazz ^Descriptors$FieldDescriptor fd]
    (let [g                (get-simple-type-gen clazz fd)
          containing-oneof (.getContainingOneof fd)
          cc               (u/field->camel-case containing-oneof)
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
  [^Class clazz ^Descriptors$FieldDescriptor fd ctx]
  (let [cc             (u/field->camel-case fd)
        inner-type     (get-parameterized-type 0 clazz fd)
        wrapper        (w/gen-wrapper inner-type ctx)
        clear-method   (symbol (str ".clear" cc))
        add-all-method (symbol (str ".addAll" cc))
        x              (gensym 'x)]
    (reify TypeGen

      (get-class [_] inner-type)

      (gen-setter [_ builder v]
        `(if-not (and (some? ~v) (.isAssignableFrom Iterable (class ~v)))
           (throw (u/make-type-error ~clazz ~(.getName fd) Iterable ~v))
           (let [al# (java.util.ArrayList. (count ~v))]
             (doseq [~x ~v]
               (.add al# ~(w/unwrap wrapper x)))
             (~add-all-method (~clear-method ~builder) al#))))

      (gen-getter [_ o]
        (let [v (with-meta (gensym 'v) {:tag 'java.util.List})]
          `(let [~v  (~(symbol (str ".get" cc "List")) ~o)
                 al# (java.util.ArrayList. (.size ~v))]
             (doseq [~x ~v]
               (.add al# ~(w/wrap wrapper x)))
             (clojure.lang.PersistentVector/create al#)))))))

(defn get-fields [^Class clazz ctx]
  (let [class-descriptor  (descriptor clazz)
        field-descriptors (field-descriptors class-descriptor)]
    (for [fd field-descriptors]
      (let [ctx (assoc ctx :class clazz :fd fd)]
        {:fd       fd
         :type-gen (get-type-gen clazz fd ctx)
         :kw       (keyword ((or (:key-name-fn ctx) identity) (.getName ^Descriptors$FieldDescriptor fd)))}))))
