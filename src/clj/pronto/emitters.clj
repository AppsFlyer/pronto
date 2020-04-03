(ns pronto.emitters
  (:require [pronto.type-gen :as t]
            [pronto.utils :as u]
            [clojure.string :as s])
  (:import [com.google.protobuf
            Descriptors$FieldDescriptor
            Descriptors$OneofDescriptor]))


(defn emit-fields-case [fields k throw-error? f]
  `(case ~k
     ~@(interleave
         (map :kw fields)
         (map f fields))
     ~(if-not throw-error?
        nil ; explicitly return nil from case
        `(throw (IllegalArgumentException. (str "No such field " ~k))))))

(defn emit-assoc [fields builder k v]
  (emit-fields-case fields k true
                    #(t/gen-setter (:type-gen %) builder k v)))


(defn emit-val-at [fields obj k]
  (emit-fields-case fields k false
                    #(t/gen-getter (:type-gen %) obj k)))

(defn emit-clear [fields builder k]
  (emit-fields-case fields k true
                    (fn [field]
                      (let [clear-method (symbol (str ".clear" (u/field->camel-case (:fd field))))]
                        `(~clear-method ~builder)))))

(defn emit-has-field? [fields o k]
  (emit-fields-case fields k true
                    (fn [field]
                      (let [^Descriptors$FieldDescriptor fd (:fd field)]
                        (if (and (u/message? fd)
                                 (not (.isMapField fd))
                                 (not (.isRepeated fd)))
                          (let [has-method (symbol (str ".has" (u/field->camel-case (:fd field))))]
                            `(~has-method ~o))
                          `(throw (IllegalArgumentException. (str "field " ~k " cannot be checked for field existence"))))))))

(defn enum-case->kebab-case [enum-case-name]
  (keyword (s/lower-case (u/->kebab-case enum-case-name))))

(defn emit-which-one-of [fields o k]
  (let [one-ofs (->> fields
                     (map #(.getContainingOneof (:fd %)))
                     (keep identity)
                     set)]
    (if-not (seq one-ofs)
      `(throw (IllegalArgumentException. (str "Cannot check which one-of for " ~k)))
      `(case ~k
         ~@(interleave
             (map #(keyword (u/->kebab-case (.getName %)))
                  one-ofs)

             (map (fn [^Descriptors$OneofDescriptor fd]
                    (let [cc               (u/field->camel-case fd)
                          case-enum-getter (symbol (str ".get" cc "Case"))]
                      `(enum-case->kebab-case (str (~case-enum-getter ~o)))))
                  one-ofs))
         (throw (IllegalArgumentException. (str "Cannot check which one-of for " ~k)))))))

(defn static-call [^Class class method-name]
  (symbol (str (.getName class) "/" method-name)))


(defn get-builder-class [^Class clazz]
  (.getReturnType (.getDeclaredMethod clazz "toBuilder" (make-array Class 0))))


(defn emit-deftype [^Class clazz]
  (let [fields              (t/get-fields clazz)
        o                   (u/with-type-hint (gensym 'o) clazz)
        builder-class       (get-builder-class clazz)
        wrapper-class-name  (u/class->map-class-name clazz)
        transient-ctor-name (u/transient-ctor-name clazz)
        builder-sym         (u/with-type-hint (gensym 'builder) builder-class)]
    `(deftype ~wrapper-class-name [~o]

       java.io.Serializable

       pronto.ProtoMap


       (~'isMutable [this#] false)

       (~'getProto [this#] ~o)

       ~(let [k (gensym 'k)]
          `(~'clearField [this# ~k]
            (let [~builder-sym (.toBuilder ~o)]
              ~(emit-clear fields builder-sym k)
              (new ~wrapper-class-name (.build ~builder-sym)))))

       ~(let [k (gensym 'k)]
          `(~'hasField [this# ~k]
            ~(emit-has-field? fields o k)))

       ~(let [k (gensym 'k)]
          `(~'whichOneOf [this# ~k]
            ~(emit-which-one-of fields o k)))

       clojure.lang.IObj

       (withMeta [this# meta-map#]
         this#)

       (meta [this#] {})

       clojure.lang.Associative

       ~(let [this (gensym 'this)
              k    (gensym 'k)
              v    (gensym 'v)]
          `(~'assoc [~this ~k ~v]
            (let [~builder-sym (.toBuilder ~o)]
              ~(emit-assoc fields builder-sym k v)
              (new ~wrapper-class-name (.build ~builder-sym)))))


       (containsKey [this# k#]
         (boolean (get ~(into #{} (map :kw fields))
                       k#)))

       (entryAt [this# k#]
         (clojure.lang.MapEntry/create k# (.valAt this# k#)))

       clojure.lang.MapEquivalence
       clojure.lang.IPersistentMap

       (without [this# k#]
         (throw (UnsupportedOperationException. "cannot dissoc from a proto map")))

       clojure.lang.ILookup

       ~(let [k (gensym 'k)]
          `(valAt [_ ~k]
                  ~(emit-val-at fields o k)))

       (valAt [this# k# not-found#]
         (.valAt this# k#))


       clojure.lang.IPersistentCollection

       (cons [this# o#]
         (pronto.PersistentMapHelpers/cons this# o#))

       (empty [this#]
         (new ~wrapper-class-name
              (.build (~(static-call clazz "newBuilder")))))

       (count [this#] ~(count fields))

       (equiv [this# other#]
         (pronto.PersistentMapHelpers/equiv this#
                                                       (if (instance? ~clazz other#)
                                                         (~(u/proto-ctor-name clazz) other#)
                                                         other#)))

       clojure.lang.Seqable

       ~(let [this (gensym 'this)]
          `(seq [~this]
                (clojure.lang.ArraySeq/create
                  (object-array
                    [~@(map (fn [fd]
                              `(.entryAt ~this ~(:kw fd)))
                            fields)]))))

       pronto.DefaultingFn

       (invoke [this# arg1#]
         (pronto.PersistentMapHelpers/invoke this# arg1#))

       (invoke [this# arg1# not-found#]
         (pronto.PersistentMapHelpers/invoke this# arg1# not-found#))

       clojure.lang.IEditableCollection

       ;; TODO: clean this up
       (asTransient [this#]
         (~transient-ctor-name (.toBuilder ~o) true))

       java.lang.Iterable

       ~(let [this (gensym 'this)]
          `(iterator [~this]
                     (clojure.lang.ArrayIter/create
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
         (pronto.PersistentMapHelpers/equals this# obj#)))))


(defn check-editable! [editable?]
  (when-not editable?
    (throw (IllegalAccessError. "Transient used after persistent! call"))))

(defn emit-transient [^Class clazz]
  (let [fields                       (t/get-fields clazz)
        builder-class                (get-builder-class clazz)
        o                            (u/with-type-hint 'o builder-class)
        transient-wrapper-class-name (u/class->transient-class-name clazz)]
    `(deftype ~transient-wrapper-class-name [~o ~(with-meta 'editable? {:unsynchronized-mutable true})]

       java.io.Serializable

       pronto.ProtoMap

       ;; TODO: remove this.
       (~'getProto [this#] ~o)

       (~'isMutable [this#] true)

       ~(let [k (gensym 'k)]
          `(~'clearField [this# ~k]
            ~(emit-clear fields o k)
            this#))

       ~(let [k (gensym 'k)]
          `(~'hasField [this# ~k]
            ~(emit-has-field? fields o k)))

       ~(let [k (gensym 'k)]
          `(~'whichOneOf [this# ~k]
            ~(emit-which-one-of fields o k)))

       clojure.lang.ITransientMap

       ~(let [this (gensym 'this)
              k    (gensym 'k)
              v    (gensym 'v)]
          `(~'assoc [~this ~k ~v]
            (check-editable! ~'editable?)
            ~(emit-assoc fields o k v)
            ~this))

       (without [this# k#]
         (check-editable! ~'editable?)
         (throw (UnsupportedOperationException. "cannot dissoc from a proto map")))

       (persistent [this#]
         (set! ~'editable? false)
         (~(u/proto-ctor-name clazz) (.build ~o)))

       (count [this#]
         (check-editable! ~'editable?)
         ~(count fields))

       clojure.lang.ITransientAssociative2

       (containsKey [this# k#]
         (check-editable! ~'editable?)
         (boolean (get ~(into #{} (map :kw fields))
                       k#)))

       (entryAt [this# k#]
         (clojure.lang.MapEntry/create k# (.valAt this# k#)))


       pronto.DefaultingFn

       (invoke [this# arg1#]
         (check-editable! ~'editable?)
         (.valAt this# arg1#))

       (invoke [this# arg1# not-found#]
         (check-editable! ~'editable?)
         (.valAt this# arg1# not-found#))

       clojure.lang.ITransientCollection

       (conj [this# val#]
         (check-editable! ~'editable?)
         (pronto.TransientMapHelpers/conj this# val#))

       clojure.lang.ILookup

       ~(let [k (gensym 'k)]
          `(valAt [this# ~k]
                  (check-editable! ~'editable?)
                  ~(emit-val-at fields o k)))

       (valAt [this# k# not-found#]
         (.valAt this# k#)))))

(defn reverse-ctor-name [ctor-name]
  (let [[x y] (s/split (str ctor-name) #"->")]
    (symbol (str y "->" x))))

(defn emit-proto-ctor [^Class clazz]
  (let [wrapper-class-name (u/class->map-class-name clazz)
        fn-name            (u/proto-ctor-name clazz)]
    `(do
       (def ~fn-name
         (fn
           [o#]
           (if(instance? ~clazz o#)
             (new ~wrapper-class-name o#)
             (throw (IllegalArgumentException. (str "cannot wrap " (or (class o#) "nil")))))))

       (def ~(reverse-ctor-name fn-name)
         (fn [o#]
           (pronto.RT/getProto o#))))))

(defn proto-map->clj-map [m]
  (into {}
        (map (fn [[k v]]
               [k (if (instance? pronto.ProtoMap v)
                    (proto-map->clj-map v)
                    v)]))
        m))

(defn emit-map-ctor [^Class clazz]
  (let [wrapper-class-name (u/class->map-class-name clazz)
        fn-name            (u/map-ctor-name clazz)]
    `(do
       (def ~fn-name
         (fn [o#]
           (if (map? o#)
             (let [res# (new ~wrapper-class-name (.build (~(static-call clazz "newBuilder"))))]
               (reduce (fn [acc# [k# v#]]
                         (assoc acc# k# v#))
                       res#
                       o#))
             (throw (IllegalArgumentException. (str "cannot wrap " (or (class o#) "nil")))))))

       (def ~(reverse-ctor-name fn-name)
         (fn [o#]
           (proto-map->clj-map o#))))))

(defn emit-default-ctor [^Class clazz]
  (let [map-ctor-name (u/map-ctor-name clazz)
        fn-name       (u/empty-ctor-name clazz)]
    `(def ~fn-name
       (fn []
         (~map-ctor-name {})))))

(defn emit-bytes-ctor [^Class clazz]
  (let [proto-ctor-name (u/proto-ctor-name clazz)
        fn-name         (u/bytes-ctor-name clazz)
        bytea           (with-meta 'bytea {:tag "[B"})
        x               (u/with-type-hint 'x clazz)]
    `(do
       (def ~fn-name
         (fn [~bytea]
           (let [proto# (~(static-call clazz "parseFrom") ~bytea)]
             (~proto-ctor-name proto#))))

       (def ~(reverse-ctor-name fn-name)
         (fn [y#]
           (let [~x (pronto.RT/getProto y#)]
             (.toByteArray ~x)))))))


(defn emit-proto-map [^Class clazz]
  `(do
     (declare ~(u/map-ctor-name clazz))
     (declare ~(u/proto-ctor-name clazz))
     (declare ~(u/transient-ctor-name clazz))

     ~(emit-deftype clazz)
     ~(emit-transient clazz)
     ~(emit-proto-ctor clazz)
     ~(emit-map-ctor clazz)
     ~(emit-bytes-ctor clazz)
     ~(emit-default-ctor clazz)))
