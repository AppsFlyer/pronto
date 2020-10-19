(ns pronto.type-gen
  (:require
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
    [pronto TransformIterable TransformIterable$Xf Utils Utils$PairXf]))


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
            `(when (or ~(boolean (:pumped? ctx)) (~has-method ~o))
               ~get-form)))))))

(defn descriptor-type [^Descriptors$FieldDescriptor fd]
  (cond
    (.isMapField fd) :map
    (.isRepeated fd) :repeated
    ;;    (.getContainingOneof fd) :one-of
    :else :simple))

(defmulti get-type-gen
  (fn [^Class _clazz
       ^Descriptors$FieldDescriptor$Type fd
       _ctx]
    (descriptor-type fd)))

(defmethod get-type-gen
  :simple
  [^Class clazz ^Descriptors$FieldDescriptor fd ctx]
  (get-simple-type-gen clazz fd ctx))

(defn find-type [^Class clazz ^Descriptors$FieldDescriptor fd]
  (.getGenericReturnType
    (.getDeclaredMethod
      clazz
      (str "get" (u/field->camel-case fd)
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

(defmethod get-type-gen
  :map
  [^Class clazz ^Descriptors$FieldDescriptor fd ctx]
  (let [cc (u/field->camel-case fd)
        key-type (get-parameterized-type 0 clazz fd)
        val-type (get-parameterized-type 1 clazz fd)
        key-wrapper (if (= String key-type)
                      (reify w/Wrapper
                        (wrap [_ v]
                          ;; -> keyword
                          `(keyword ~v))

                        (unwrap [_ v]
                          ;; -> string
                          `(name ~v)))
                      (w/gen-wrapper key-type ctx))
        val-wrapper (w/gen-wrapper val-type ctx)
        clear-method (symbol (str ".clear" cc))
        put-all-method (symbol (str ".putAll" cc))
        m (u/with-type-hint (gensym 'm) java.util.Map)]
    (reify TypeGen

      (get-class [_] val-type)

      (gen-setter [_ builder v]
        `(if (nil? ~v)
           (throw (u/make-type-error ~clazz ~(.getName fd) java.util.Map ~v))
           (let [~m       ~v
                 ~builder (~clear-method ~builder)]
             (~put-all-method ~builder
               (Utils/transformMap ~v
                            (reify Utils$PairXf
                                     (transformKey [_ keyItem]
                                       ~(w/unwrap key-wrapper 'keyItem))
                                     (transformVal [_ valItem]
                                       ~(w/unwrap val-wrapper 'valItem))))))))

      (gen-getter [_ o]
        `(let [~m       (~(symbol (str ".get" cc "Map")) ~o)]
           (clojure.lang.PersistentArrayMap.
             (Utils/mapToArray ~m
                               (reify Utils$PairXf
                                 (transformKey [_ keyItem]
                                   ~(w/wrap key-wrapper 'keyItem))
                                 (transformVal [_ valItem]
                                   ~(w/wrap val-wrapper 'valItem)))))
           )))))

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
        get-list       (symbol (str ".get" cc "List"))]
    (reify TypeGen

      (get-class [_] inner-type)

      (gen-setter [_ builder v]
        `(if (nil? ~v)
           (throw (u/make-type-error ~clazz ~(.getName fd) Iterable ~v))
           (~add-all-method (~clear-method ~builder)
             (TransformIterable. ~v
                                 (reify TransformIterable$Xf
                                   (transform [_ item]
                                     ~(w/unwrap wrapper 'item)))))))

      (gen-getter [_ o]
        `(let [^java.util.List v# (~get-list ~o)]
           (clojure.lang.PersistentVector/adopt
             (Utils/iterableToArray
               (TransformIterable. v#
                                   (reify TransformIterable$Xf
                                     (transform [_ item]
                                       ~(w/wrap wrapper 'item))))
               (.size v#))))))))

(defn get-fields [^Class clazz ctx]
  (let [class-descriptor  (descriptor clazz)
        field-descriptors (field-descriptors class-descriptor)]
    (for [fd field-descriptors]
      (let [ctx (assoc ctx :class clazz :fd fd)]
        {:fd       fd
         :type-gen (get-type-gen clazz fd ctx)
         :kw       (keyword ((or (:key-name-fn ctx) identity) (.getName ^Descriptors$FieldDescriptor fd)))}))))
