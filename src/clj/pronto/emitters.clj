(ns pronto.emitters
  (:require [pronto.type-gen :as t]
            [pronto.utils :as u]
            [clojure.string :as s])
  (:import [com.google.protobuf
            Descriptors$FieldDescriptor
            Descriptors$OneofDescriptor]
           [com.google.protobuf Internal$EnumLite]
           [java.lang.reflect Method]))


(defn get-builder-class [^Class clazz]
  (.getReturnType (.getDeclaredMethod clazz "toBuilder" (make-array Class 0))))


(defn- proto-or-builder-interface [^Class clazz]
  ;; TODO: not the best way to go about this.
  (first (.getInterfaces clazz)))


(defn- interface-name [^Class clazz]
  (symbol (str 'I (s/replace (.getName clazz) "." "_"))))


(defn- assoc-intf-name [^Descriptors$FieldDescriptor fd]
  (symbol (str "assoc" (u/field->camel-case fd))))


(defn- val-at-intf-name [^Descriptors$FieldDescriptor fd]
  (symbol (str "valAt" (u/field->camel-case fd))))

(defn emit-reduce [clauses]
  (let [r (gensym 'res)]
    (reduce (fn [acc f]
              `(let [~r ~f]
                 (if (reduced? ~r)
                   ~r
                   ~acc)))
            r
            clauses)))

(defn- emit-interface [^Class clazz ctx]
  (let [fields (t/get-fields clazz ctx)]
    `(definterface ~(interface-name clazz)
       ~@(let [builder-class   (get-builder-class clazz)
               builder-sym     (u/with-type-hint (gensym 'builder) builder-class)
               proto-interface (proto-or-builder-interface clazz)
               proto-sym       (u/with-type-hint (gensym 'proto) proto-interface)]
           (concat
             (for [field fields]
               `(~(u/with-type-hint (assoc-intf-name (:fd field))
                    builder-class)
                 [~builder-sym val#]))

             (for [field fields]
               `(~(val-at-intf-name (:fd field))
                 [~proto-sym])))))))


(defn- delegate-method [^Method method delegate-sym]
  (let [; parameter-types (.getParameterTypes method)
        args        (repeatedly (.getParameterCount method) gensym)
        #_          (map #(if-not (get #{Integer/TYPE} %)
                            (u/with-type-hint (gensym) %)
                            (gensym))
                         parameter-types)
        method-name (symbol (.getName method))
        #_          (u/with-type-hint (symbol (.getName method))
                      (.getReturnType method))
        ]
    `(~method-name [this# ~@args]
      (. ~delegate-sym ~(symbol (.getName method)) ~@args))))


(defn- implement-message-or-builder-interface [^Class clazz delegate-sym]
  (let [^Class interface (proto-or-builder-interface clazz)]
    `(~(symbol (.getName interface))
      ~@(->> (.getMethods interface)
             ;; TODO: currently, only take methods from the most specific
             ;; interface - need to expand to super-interfaces
             (filter #(= interface (.getDeclaringClass ^Method %)))
             (map #(delegate-method ^Method % delegate-sym))))))

(defn emit-fields-case [fields k throw-error? f]
  `(case ~k
     ~@(interleave
         (map :kw fields)
         (map f fields))
     ~(if-not throw-error?
        nil ; explicitly return nil from case
        `(throw (IllegalArgumentException. (str "No such field " ~k))))))


(defn emit-assoc [fields this builder k v]
  (emit-fields-case fields k true
                    (fn [fd]
                      `(~(symbol (str "." (assoc-intf-name (:fd fd)))) ~this ~builder ~v))))


(defn emit-val-at [fields this obj k]
  (emit-fields-case fields k true
                    (fn [fd]
                      `(~(symbol (str "." (val-at-intf-name (:fd fd)))) ~this ~obj))))

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
                     (map #(.getContainingOneof ^Descriptors$FieldDescriptor (:fd %)))
                     (keep identity)
                     set)]
    (if-not (seq one-ofs)
      `(throw (IllegalArgumentException. (str "Cannot check which one-of for " ~k)))
      `(case ~k
         ~@(interleave
             (map #(keyword (u/->kebab-case (.getName ^Descriptors$OneofDescriptor %)))
                  one-ofs)

             (map (fn [^Descriptors$OneofDescriptor fd]
                    (let [cc               (u/field->camel-case fd)
                          case-enum-getter (symbol (str ".get" cc "Case"))
                          v                (u/with-type-hint (gensym 'oo) Internal$EnumLite)]
                      `(let [~v (~case-enum-getter ~o)]
                         (when-not (zero? (.getNumber ^Internal$EnumLite ~v))
                           (enum-case->kebab-case (str (~case-enum-getter ~o)))))))
                  one-ofs))
         (throw (IllegalArgumentException. (str "Cannot check which one-of for " ~k)))))))


(defprotocol ProtoMapBuilder
  (proto->proto-map [this]))


(defn emit-default-ctor [^Class clazz]
  (let [wrapper-class-name (u/class->map-class-name clazz)]
    `(new ~wrapper-class-name (.build (~(u/static-call clazz "newBuilder"))) nil)))


(defn emit-default-transient-ctor [^Class clazz ns]
  (let [transient-wrapper-class-name (u/class->transient-class-name clazz)]
    `(~(symbol ns (str '-> transient-wrapper-class-name)) (~(u/static-call clazz "newBuilder")) true)))


(defn emit-interface-impl [^Class clazz fields]
  `(~@(let [builder-class   (get-builder-class clazz)
            builder-sym     (u/with-type-hint (gensym 'builder) builder-class)
            proto-interface (proto-or-builder-interface clazz)
            proto-sym       (u/with-type-hint (gensym 'proto) proto-interface)]
        (concat
          (for [field fields]
            (let [val-sym (gensym 'val)]
              `(~(u/with-type-hint (assoc-intf-name (:fd field))
                   builder-class)
                [this# ~builder-sym ~val-sym]
                ~(t/gen-setter (:type-gen field) builder-sym val-sym))))

          (for [field fields]
            `(~(val-at-intf-name (:fd field))
              [this# ~proto-sym]
              ~(t/gen-getter (:type-gen field) proto-sym)))))))


(defn emit-deftype [^Class clazz ctx]
  (let [fields               (t/get-fields clazz ctx)
        o                    (u/with-type-hint (gensym 'o) clazz)
        builder-class        (get-builder-class clazz)
        wrapper-class-name   (u/class->map-class-name clazz)
        transient-class-name (u/class->transient-class-name clazz)
        builder-sym          (u/with-type-hint (gensym 'builder) builder-class)
        md                   (gensym 'md)]
    `(deftype ~wrapper-class-name [~o ~md]

       java.io.Serializable

       pronto.ProtoMap

       (~'pmap_isMutable [this#] false)

       (~'pmap_getProto [this#] ~o)

       ~(let [k (gensym 'k)]
          `(~'pmap_clearField [this# ~k]
            (let [~builder-sym (.toBuilder ~o)]
              ~(emit-clear fields builder-sym k)
              (new ~wrapper-class-name (.build ~builder-sym) ~md))))

       ~(let [k (gensym 'k)]
          `(~'pmap_hasField [this# ~k]
            ~(emit-has-field? fields o k)))

       ~(let [k (gensym 'k)]
          `(~'pmap_whichOneOf [this# ~k]
            ~(emit-which-one-of fields o k)))

       (pmap_inflate [this#]
         (new ~(u/class->pumped-map-class-name clazz) ~o ~md))

       (pmap_deflate [this#]
         this#)

       clojure.lang.IObj

       (withMeta [this# meta-map#]
         (if (nil? meta-map#)
           this#
           (new ~wrapper-class-name ~o meta-map#)))

       (meta [this#] ~md)

       clojure.lang.Associative

       ~(let [this (gensym 'this)
              k    (gensym 'k)
              v    (gensym 'v)]
          `(~'assoc [~this ~k ~v]
            (let [~builder-sym (.toBuilder ~o)]
              ~(emit-assoc fields this builder-sym k v)
              (new ~wrapper-class-name (.build ~builder-sym) ~md))))


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

       ~(let [k    (gensym 'k)
              this (gensym 'this)]
          `(valAt [~this ~k]
                  ~(emit-val-at fields this o k)))

       (valAt [this# k# not-found#]
         (.valAt this# k#))


       clojure.lang.IPersistentCollection

       (cons [this# o#]
         (pronto.PersistentMapHelpers/cons this# o#))

       (empty [this#]
         ;; TODO: create a default instance
         (new ~wrapper-class-name
              (.build (~(u/static-call clazz "newBuilder")))
              nil))

       (count [this#] ~(count fields))

       (equiv [this# other#]
         (pronto.PersistentMapHelpers/equiv this#
                                                       (if (instance? ~clazz other#)
                                                         (new ~wrapper-class-name other# nil)
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
         (new ~transient-class-name (.toBuilder ~o) true))

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
         (pronto.PersistentMapHelpers/equals this# obj#))

       ~@(implement-message-or-builder-interface clazz o)

       ~(interface-name clazz)

       ~@(emit-interface-impl clazz fields)

       clojure.lang.IKVReduce

       ~(let [this (gensym 'this)
              f    (gensym 'f)
              init (gensym 'init)]
          `(~'kvreduce [~this ~f ~init]
            ~(emit-reduce
               (map
                 (fn [fd]
                   `(~f (~(symbol (str "." (val-at-intf-name (:fd fd)))) ~this ~o)))
                 fields)))))))

(defn emit-pump [^Class clazz ctx]
  (let [ctx                  (assoc ctx :pumped? true)
        fields               (t/get-fields clazz ctx)
        o                    (u/with-type-hint (gensym 'o) clazz)
        builder-class        (get-builder-class clazz)
        wrapper-class-name   (u/class->pumped-map-class-name clazz)
        transient-class-name (u/class->transient-class-name clazz)
        builder-sym          (u/with-type-hint (gensym 'builder) builder-class)
        md                   (gensym 'md)]
    `(deftype ~wrapper-class-name [~o ~md]

       java.io.Serializable

       pronto.ProtoMap

       (~'pmap_isMutable [this#] false)

       (~'pmap_getProto [this#] ~o)

       ~(let [k (gensym 'k)]
          `(~'pmap_clearField [this# ~k]
            (let [~builder-sym (.toBuilder ~o)]
              ~(emit-clear fields builder-sym k)
              (new ~wrapper-class-name (.build ~builder-sym) ~md))))

       ~(let [k (gensym 'k)]
          `(~'pmap_hasField [this# ~k]
            ~(emit-has-field? fields o k)))

       ~(let [k (gensym 'k)]
          `(~'pmap_whichOneOf [this# ~k]
            ~(emit-which-one-of fields o k)))

       (pmap_inflate [this#]
         this#)

       (pmap_deflate [this#]
         (new ~(u/class->map-class-name clazz) ~o ~md))

       clojure.lang.IObj

       (withMeta [this# meta-map#]
         (if (nil? meta-map#)
           this#
           (new ~wrapper-class-name ~o meta-map#)))

       (meta [this#] ~md)

       clojure.lang.Associative

       ~(let [this (gensym 'this)
              k    (gensym 'k)
              v    (gensym 'v)]
          `(~'assoc [~this ~k ~v]
            (let [~builder-sym (.toBuilder ~o)]
              ~(emit-assoc fields this builder-sym k v)
              (new ~wrapper-class-name (.build ~builder-sym) ~md))))


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

       ~(let [k    (gensym 'k)
              this (gensym 'this)]
          `(valAt [~this ~k]
                  ~(emit-val-at fields this o k)))

       (valAt [this# k# not-found#]
         (.valAt this# k#))


       clojure.lang.IPersistentCollection

       (cons [this# o#]
         (pronto.PersistentMapHelpers/cons this# o#))

       (empty [this#]
         ;; TODO: create a default instance
         (new ~wrapper-class-name
              (.build (~(u/static-call clazz "newBuilder")))
              nil))

       (count [this#] ~(count fields))

       (equiv [this# other#]
         (pronto.PersistentMapHelpers/equiv this#
                                                       (if (instance? ~clazz other#)
                                                         (new ~wrapper-class-name other# nil)
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
         (new ~transient-class-name (.toBuilder ~o) true))

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
         (pronto.PersistentMapHelpers/equals this# obj#))

       ~@(implement-message-or-builder-interface clazz o)

       ~(interface-name clazz)

       ~@(emit-interface-impl clazz fields))))


(defn check-editable! [editable?]
  (when-not editable?
    (throw (IllegalAccessError. "Transient used after persistent! call"))))

(defn emit-transient [^Class clazz ctx]
  (let [fields                       (t/get-fields clazz ctx)
        builder-class                (get-builder-class clazz)
        o                            (u/with-type-hint 'o builder-class)
        transient-wrapper-class-name (u/class->transient-class-name clazz)
        wrapper-class-name           (u/class->map-class-name clazz)]
    `(deftype ~transient-wrapper-class-name [~o ~(with-meta 'editable? {:unsynchronized-mutable true})]

       java.io.Serializable

       pronto.ProtoMap

       ;; TODO: remove this.
       (~'pmap_getProto [this#] ~o)

       (~'pmap_isMutable [this#] true)

       ~(let [k (gensym 'k)]
          `(~'pmap_clearField [this# ~k]
            ~(emit-clear fields o k)
            this#))

       ~(let [k (gensym 'k)]
          `(~'pmap_hasField [this# ~k]
            ~(emit-has-field? fields o k)))

       ~(let [k (gensym 'k)]
          `(~'pmap_whichOneOf [this# ~k]
            ~(emit-which-one-of fields o k)))

       clojure.lang.ITransientMap

       ~(let [this (gensym 'this)
              k    (gensym 'k)
              v    (gensym 'v)]
          `(~'assoc [~this ~k ~v]
            (check-editable! ~'editable?)
            ~(emit-assoc fields this o k v)
            ~this))

       (without [this# k#]
         (check-editable! ~'editable?)
         (throw (UnsupportedOperationException. "cannot dissoc from a proto map")))

       (persistent [this#]
         (set! ~'editable? false)
         (new ~wrapper-class-name (.build ~o) nil))

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

       ~(let [k    (gensym 'k)
              this (gensym 'this)]
          `(valAt [~this ~k]
                  (check-editable! ~'editable?)
                  ~(emit-val-at fields this o k)))

       (valAt [this# k# not-found#]
         (.valAt this# k#))

       ;; TODO: move clearers, getters to interface as well, instead of inlining them
       ~(interface-name clazz)

       ~@(emit-interface-impl clazz fields))))


(defn- emit-mapper [^Class clazz]
  (let [proto-map-class-name (u/class->map-class-name clazz)]
    `(extend-type ~clazz
       ProtoMapBuilder
       (proto->proto-map [this#]
         (new ~proto-map-class-name this# nil)))))


(defn- declare-class [class-name nargs]
  `(deftype ~class-name [~@(repeatedly nargs gensym)]))


(defn empty-map-var-name [^Class clazz]
  (symbol (str "__PROTOCLJ_EMPTY_" (s/replace (.getName clazz) "." "_"))))


(defn emit-empty-map [^Class clazz]
  `(def ~(empty-map-var-name clazz) #_~(with-meta (singleton-name clazz)  {:const true}) ~(emit-default-ctor clazz)))


(defn emit-proto-map [^Class clazz ctx]
  `(do
     ~(declare-class (u/class->map-class-name clazz) 2)
     ~(declare-class (u/class->pumped-map-class-name clazz) 2)
     ~(declare-class (u/class->transient-class-name clazz) 2)
     ~(emit-interface clazz ctx)
     ~(emit-deftype clazz ctx)
     ~(emit-transient clazz ctx)
     ~(emit-pump clazz ctx)
     ~(emit-empty-map clazz)
     ~(emit-mapper clazz)))

