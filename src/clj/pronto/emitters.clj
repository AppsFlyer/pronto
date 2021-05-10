(ns pronto.emitters
  (:require [pronto.type-gen :as t]
            [pronto.utils :as u]
            [pronto.protos :refer [global-ns]]
            [clojure.string :as s]
            [pronto.reflection :as reflect])
  (:import [com.google.protobuf
            Descriptors$FieldDescriptor
            Descriptors$OneofDescriptor]
           [com.google.protobuf Internal$EnumLite]
           [java.lang.reflect Method]
           [pronto ProtoMapper]))


(defn get-builder-class [^Class clazz]
  (.getReturnType (.getDeclaredMethod clazz "toBuilder" (make-array Class 0))))


(defn empty-map-var-name
  ([clazz] (empty-map-var-name clazz nil))
  ([^Class clazz ns]
   (symbol ns (str "__EMPTY_" (u/sanitized-class-name clazz)))))


(defn- proto-or-builder-interface [^Class clazz]
  ;; TODO: not the best way to go about this.
  (first (.getInterfaces clazz)))


(defn assoc-intf-name2 [{:keys [iname itype]}]
  (symbol (str "assoc" iname "_" (.getSimpleName ^Class itype))))


(defn val-at-intf-name2 [{:keys [iname itype]}]
  (symbol (str "valAt" iname "_" (.getSimpleName ^Class itype))))


(defn empty-intf-name2 [{:keys [iname]}]
  (symbol (str "empty" iname)))


(defn clear-intf-name2 [{:keys [iname]}]
  (symbol (str "clear" iname)))


(defn interface-info [k]
  {:iname (u/->camel-case (name k))
   :itype Object})

(defn- fd->interface-info [fd]
  (interface-info (:kw fd)))


(defn intf-info->intf-name [{:keys [iname itype]}]
  (symbol (str 'I iname "_" (u/sanitized-class-name itype))))


(defn clear [field builder]
  (let [clear-method (symbol (str ".clear" (u/field->camel-case (:fd field))))]
    `(~clear-method ~(u/with-type-hint builder
                       (get-builder-class (:class field))))))


(defn emit-default-ctor [^Class clazz]
  (let [wrapper-class-name (u/class->map-class-name clazz)]
    `(new ~wrapper-class-name (.build (~(u/static-call clazz "newBuilder"))) nil)))


(defn emit-default-transient-ctor [^Class clazz ns]
  (let [transient-wrapper-class-name (u/class->transient-class-name clazz)]
    `(~(symbol ns (str '-> transient-wrapper-class-name)) (~(u/static-call clazz "newBuilder")) true false)))


(defn get-interfaces [^Class clazz ctx]
  (let [fields          (t/get-fields clazz ctx)
        builder-class   (get-builder-class clazz)
        builder-sym     (gensym 'builder) #_ (u/with-type-hint (gensym 'builder) builder-class)
        proto-interface (proto-or-builder-interface clazz)
        val-sym         (gensym 'val)
        this            (gensym 'this)]
    (for [field fields]
      (let [intf-info (fd->interface-info field)
            intf-name (intf-info->intf-name intf-info)
            ^Descriptors$FieldDescriptor fd (:fd field)]
        {:name (str (u/javaify global-ns) "." intf-name)
         :intf
         `(definterface ~intf-name
            (~(assoc-intf-name2 intf-info)
             [~'builder ~'val])

            (~(val-at-intf-name2 intf-info)
             [])

            (~(empty-intf-name2 intf-info)
             [])

            (~(clear-intf-name2 intf-info)
             [~'builder]))

         :impl
         `((~(assoc-intf-name2 intf-info)
            [~this ~builder-sym ~val-sym]
            ~(let [ex (gensym 'ex)]
               `(try
                  ~(t/gen-setter
                     (:type-gen field)
                     (u/with-type-hint
                       builder-sym
                       builder-class)
                     val-sym)
                  (catch ClassCastException ~ex
                      (throw ~(u/make-type-error
                                clazz
                                (:kw field)
                                (cond
                                  (.isMapField fd) java.util.Map
                                  (.isRepeated fd) java.util.List
                                  :else (t/field-type clazz fd))
                                val-sym
                                ex))))))
           

           (~(val-at-intf-name2 intf-info)
            [~this]
            ~(t/gen-getter
               (:type-gen field)
               (u/with-type-hint
                 this
                 proto-interface)))

           (~(empty-intf-name2 intf-info)
            [~this]
            ~(let [^Descriptors$FieldDescriptor fd (:fd field)]
               (if (u/struct? fd)
                 (empty-map-var-name (t/field-type clazz (:fd field)))
                 `(throw (new UnsupportedOperationException
                              "Cannot call empty")))))

           (~(clear-intf-name2 intf-info)
            [~this ~builder-sym]
            ~(clear field builder-sym)))}))))


(defn- emit-interfaces
  [interfaces]
  (let [new-interfaces (filter
                         (comp
                           (complement reflect/class-defined?)
                           name
                           :name)
                         interfaces)]
    `(u/with-ns ~global-ns
       (do
         ~@(for [intf new-interfaces]
             (:intf intf))))))


(def from-bytes-method 'fromBytes)
(def get-transient-method 'getTransient)
(def get-proto-method 'getProto)


(defn builder-interface-name [^Class clazz]
  (symbol (str "Builder_" (u/sanitized-class-name clazz))))

(defn builder-interface-get-proto-method-name [^Class clazz]
  (symbol (str "getProto_" (u/sanitized-class-name clazz))))

(defn builder-interface-from-proto-method-name [^Class clazz]
  (symbol (str "fromProto_" (u/sanitized-class-name clazz))))

(defn builder-interface-from-bytes-method-name [^Class clazz]
  (symbol (str from-bytes-method "_" (u/sanitized-class-name clazz))))

(defn builder-interface-get-transient-method-name [^Class clazz]
  (symbol (str "getTransient_" (u/sanitized-class-name clazz))))

(defn- proto-builder-interface [ns ^Class clazz]
  (let [intf-name     (builder-interface-name clazz)
        proto-obj-sym (gensym 'pos)
        bytea         (gensym 'bytea)]
    {:name (symbol (str (u/javaify global-ns) "." intf-name))
     :intf
     `(definterface ~intf-name
        (~(builder-interface-get-proto-method-name clazz)
         [])

        (~(builder-interface-from-proto-method-name clazz)
         [~'proto-obj])

        (~(builder-interface-get-transient-method-name clazz)
         [])

        (~(builder-interface-from-bytes-method-name clazz)
         [~'bytea]))
     :impl
     `((~(builder-interface-get-proto-method-name clazz)
        [~'_]
        ~(empty-map-var-name clazz ns))

       (~(builder-interface-from-proto-method-name clazz)
        [~'_ ~proto-obj-sym]
        (new ~(symbol (str (u/javaify ns) "." (u/class->map-class-name clazz)))
             ~proto-obj-sym
             nil))

       (~(builder-interface-get-transient-method-name clazz)
        [~'_]
        ~(emit-default-transient-ctor clazz ns))

       (~(builder-interface-from-bytes-method-name clazz)
        [this# ~bytea]
        (. this# ~(builder-interface-from-proto-method-name clazz)
          (~(u/static-call clazz "parseFrom")
           ~(with-meta bytea {:tag "[B"})))))}))


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
             (map #(delegate-method
                    ^Method %
                    (u/with-type-hint
                      delegate-sym
                      interface)))))))


(defn- emit-case [k branches not-found]
  (let [clauses (apply concat branches)]
    (if (<= (count branches) 8)
      `(condp identical? ~k
         ~@clauses
         ~not-found)
      `(case ~k
         ~@clauses
         ~not-found))))


(defn- emit-fields-case [fields k throw-error? f]
  (let [branches  (map (juxt :kw f) fields)
        not-found (if-not throw-error? nil `(throw (IllegalArgumentException. (str "No such field " ~k))))]
    (emit-case k branches not-found)))


(defn emit-assoc [clazz fields this builder k v]
  (emit-fields-case
         fields k true
         (fn [fd]
           `(~(symbol (str "."
                           (assoc-intf-name2
                            (fd->interface-info fd))))
             ~this
             ~builder
             ~v))))



(defn direct-dispath-call [fd this-sym]
  `(~(symbol (str "." (val-at-intf-name2
                       (fd->interface-info fd))))
    ~this-sym))

(defn emit-val-at [fields this k]
  (emit-fields-case
    fields k true
    (fn [fd]
      (direct-dispath-call fd this))))


(defn emit-clear [fields builder k]
  (emit-fields-case
    fields k true
    (fn [field]
      (clear field builder))))

(defn emit-has-field? [fields o k]
  (emit-fields-case
    fields k true
    (fn [field]
      (let [^Descriptors$FieldDescriptor fd (:fd field)]
        (if (u/struct? fd)
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
             (map
               #(keyword (u/->kebab-case (.getName ^Descriptors$OneofDescriptor %)))
               one-ofs)

             (map
               (fn [^Descriptors$OneofDescriptor fd]
                 (let [cc               (u/field->camel-case fd)
                       case-enum-getter (symbol (str ".get" cc "Case"))
                       v                (u/with-type-hint
                                          (gensym 'oo)
                                          Internal$EnumLite)]
                   `(let [~v (~case-enum-getter ~o)]
                      (when-not (zero? (.getNumber ^Internal$EnumLite ~v))
                        (enum-case->kebab-case (str (~case-enum-getter ~o)))))))
               one-ofs))
         (throw (IllegalArgumentException. (str "Cannot check which one-of for " ~k)))))))


(defn- emit-empty [clazz fields k]
  (emit-fields-case
    fields
    k
    true
    (fn [field]
      (let [^Descriptors$FieldDescriptor fd (:fd field)]
        (when (u/struct? fd)
          (empty-map-var-name (t/field-type clazz fd)))))))


(defprotocol ProtoMapBuilder
  (proto->proto-map [this mapper]))


(def ^:private pojo (gensym 'pojo))


(defn emit-deftype [^Class clazz ctx]
  (let [fields               (t/get-fields clazz ctx)
        o                    (u/with-type-hint pojo clazz)
        wrapper-class-name   (u/class->map-class-name clazz)
        transient-class-name (u/class->transient-class-name clazz)
        md                   (gensym 'md)
        mapper               (gensym 'mapper)
        builder-class (get-builder-class clazz)
        builder-sym   (u/with-type-hint (gensym 'builder) builder-class)
        interfaces    (get-interfaces clazz ctx)]
    `(deftype ~wrapper-class-name [~o ~md]

       clojure.lang.IPersistentMap

       (without [this# k#]
         (throw (UnsupportedOperationException. "cannot dissoc from a proto map")))

       clojure.lang.Associative

       ~(let [this (gensym 'this)
              k    (gensym 'k)
              v    (gensym 'v)]
          `(assoc [~this ~k ~v]
                  (let [~builder-sym (.pmap_getBuilder ~this)]
                    ~(emit-assoc clazz fields this builder-sym k v)
                    (.copy ~this ~builder-sym))))

       pronto.ProtoMap

       (isMutable [this#] false)

       (pmap_getBuilder [this#] (.toBuilder ~o))

       (copy [this# builder#] (new ~wrapper-class-name (.build builder#) ~md))

       (remap [this# ~mapper]
              ~(let [mapper (with-meta mapper
                              {:tag (str (u/javaify global-ns)
                                         "."
                                         (builder-interface-name clazz))})]
                 `(. ~mapper ~(builder-interface-from-proto-method-name clazz) ~o)))

       (pmap_getProto [this#] ~pojo)
         
       ~(let [k (gensym 'k)]
          `(clearField [this# ~k]
                       (let [~builder-sym (.pmap_getBuilder this#)]
                         ~(emit-clear fields builder-sym k)
                         (.copy this# ~builder-sym))))

       ~(let [k (gensym 'k)]
          `(pmap_hasField [this# ~k]
                          ~(emit-has-field? fields o k)))
       
       ~(let [k (gensym 'k)]
          `(whichOneOf [this# ~k]
                       ~(emit-which-one-of fields o k)))

       (containsKey [this# k#]
         (boolean (get ~(into #{} (map :kw fields))
                       k#)))

       (entryAt [this# k#]
         (clojure.lang.MapEntry/create k# (.valAt this# k#)))
       
       clojure.lang.MapEquivalence

       clojure.lang.ILookup

       ~(let [k    (gensym 'k)
              this (gensym 'this)]
          `(valAt [~this ~k]
                  ~(emit-val-at fields this k)))

       (valAt [this# k# not-found#]
         (.valAt this# k#))

       pronto.DefaultingFn

       (invoke [this# arg1#]
         (pronto.PersistentMapHelpers/invoke this# arg1#))

       (invoke [this# arg1# not-found#]
         (pronto.PersistentMapHelpers/invoke this# arg1# not-found#))

       ~@(implement-message-or-builder-interface clazz o)

       ~@(mapcat
           (fn [{:keys [name impl]}]
             (into [(symbol name)]
                   impl))
           interfaces)

       java.io.Serializable

       clojure.lang.IObj

       (withMeta [this# meta-map#]
                 (if (nil? meta-map#)
                   this#
                   (new ~wrapper-class-name ~o meta-map#)))

       (meta [this#] ~md)

       clojure.lang.IPersistentCollection

       (cons [this# o#]
             (pronto.PersistentMapHelpers/cons this# o#))

       (empty
         [this#]
         ~(empty-map-var-name clazz))

       (count [this#] ~(count fields))

       (equiv [this# other#]
              (pronto.PersistentMapHelpers/equiv
                this#
                (if (instance? ~clazz other#)
                  (new ~wrapper-class-name other# nil)
                  other#)))

       clojure.lang.Seqable

       ~(let [this    (gensym 'this)
              entries (mapv (fn [fd]
                              `(clojure.lang.MapEntry/create
                                 ~(:kw fd)
                                 ~(direct-dispath-call fd this)))
                            fields)]
          `(seq
             [~this]
             ~(if (nil? (:iter-xf ctx))
                `(clojure.lang.RT/seq ~entries)
                `(sequence
                   ~(:iter-xf ctx)
                   ~entries))))

       clojure.lang.IEditableCollection

       ;; TODO: clean this up
       (asTransient [this#]
                    (new ~transient-class-name (.toBuilder ~o) true false))

       java.lang.Iterable

       ~(let [this         (gensym 'this)
              entries-iter `(clojure.lang.RT/iter
                              ~(mapv (fn [fd]
                                       `(clojure.lang.MapEntry/create
                                          ~(:kw fd)
                                          ~(direct-dispath-call fd this)))
                                     fields))]
          `(iterator
             [~this]
             ~(if (nil? (:iter-xf ctx))
                entries-iter
                `(clojure.lang.TransformerIterator/create
                   ~(:iter-xf ctx)
                   ~entries-iter))))

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

(defn emit-transient [^Class clazz ctx]
  (let [fields                       (t/get-fields clazz ctx)
        builder-class                (get-builder-class clazz)
        o                            (u/with-type-hint pojo builder-class)
        transient-wrapper-class-name (u/class->transient-class-name clazz)
        wrapper-class-name           (u/class->map-class-name clazz)
        builder-class (get-builder-class clazz)
        builder-sym   (u/with-type-hint (gensym 'builder) builder-class)
        interfaces    (get-interfaces clazz ctx)]
    `(deftype ~transient-wrapper-class-name [~(with-meta o {:unsynchronized-mutable true})
                                              ~(with-meta 'editable? {:unsynchronized-mutable true})
                                              ~(with-meta 'in-transaction? {:unsynchronized-mutable true})]

       pronto.ProtoMap

       (isMutable [this#] true)

       (pmap_getBuilder [this#] ~o)

       (copy [this# builder#]
                  (set! ~o builder#)
                  this#)

       (pmap_getProto [this#] ~pojo)
       
       ~(let [k (gensym 'k)]
          `(clearField [this# ~k]
                       (let [~builder-sym (.pmap_getBuilder this#)]
                         ~(emit-clear fields builder-sym k)
                         (.copy this# ~builder-sym))))

       ~(let [k (gensym 'k)]
          `(pmap_hasField [this# ~k]
                          ~(emit-has-field? fields o k)))

       ~(let [k (gensym 'k)]
          `(whichOneOf [this# ~k]
                       ~(emit-which-one-of fields o k)))

       ~(let [k (gensym 'k)]
          `(empty [this# ~k]
                  ~(emit-empty clazz fields k)))

       
       clojure.lang.MapEquivalence

       clojure.lang.ILookup

       ~(let [k    (gensym 'k)
              this (gensym 'this)]
          `(valAt [~this ~k]
                  ~(emit-val-at fields this k)))

       (valAt [this# k# not-found#]
         (.valAt this# k#))

       pronto.DefaultingFn

       (invoke [this# arg1#]
         (pronto.PersistentMapHelpers/invoke this# arg1#))

       (invoke [this# arg1# not-found#]
         (pronto.PersistentMapHelpers/invoke this# arg1# not-found#))

       ~@(implement-message-or-builder-interface clazz o)

       ~@(mapcat
           (fn [{:keys [name impl]}]
             (into [(symbol name)]
                   impl))
           interfaces)

       pronto.TransientProtoMap

       (setInTransaction [this# v#] (set! ~'in-transaction? v#))

       (isInTransaction [this#] ~'in-transaction?)

       clojure.lang.ITransientMap

       ~(let [this (gensym 'this)
              k    (gensym 'k)
              v    (gensym 'v)]
          `(~'assoc [~this ~k ~v]
            (check-editable! ~'editable?)
            ~(emit-assoc clazz fields this o k v)
            ~this))

       (persistent
         [this#]
         (set! ~'editable? false)
         (new ~wrapper-class-name (.build ~o) nil))

       (count [this#]
              ~(count fields))

       clojure.lang.ITransientAssociative2

       (containsKey [this# k#]
                    (check-editable! ~'editable?)
                    (boolean (get ~(into #{} (map :kw fields))
                                  k#)))

       (entryAt [this# k#]
                (clojure.lang.MapEntry/create k# (.valAt this# k#)))

       clojure.lang.ITransientCollection

       (conj [this# val#]
             (check-editable! ~'editable?)
             (pronto.TransientMapHelpers/conj this# val#)))))


(defn- emit-builder [^Class clazz]
  (let [mapper (with-meta 'mapper {:tag (str (u/javaify global-ns) "." (builder-interface-name clazz))})]
    `(extend-type ~clazz
       ProtoMapBuilder
       (proto->proto-map [this# ~mapper]
         (. ~mapper ~(builder-interface-from-proto-method-name clazz) this#)))))


(defn- declare-class [class-name nargs]
  `(deftype ~class-name [~@(repeatedly nargs gensym)]))


(defn- declare-empty-map [^Class clazz]
  `(declare ~(empty-map-var-name clazz)))


(defn- emit-empty-map [^Class clazz]
  `(def ~(empty-map-var-name clazz)
     ~(emit-default-ctor clazz)))


(defn emit-decls [classes]
  `(do
     ~@(mapcat
        (fn [clazz]
          [(declare-class (u/class->map-class-name clazz) 2)
           (declare-class (u/class->transient-class-name clazz) 3)
           (declare-empty-map clazz)])
        classes)))

(defn emit-proto-map [^Class clazz ctx]
  `(do
     ~(emit-interfaces (get-interfaces clazz ctx))
     ~(emit-interfaces [(proto-builder-interface (:ns ctx) clazz)])
     ~(emit-deftype clazz ctx)
     ~(emit-transient clazz ctx)
     ~(emit-empty-map clazz)
     ~(emit-builder clazz)))


(defn with-builder-class-hint [mapper-sym clazz]
  (with-meta
    mapper-sym
    {:tag (if clazz
            (str (u/javaify global-ns) "." (builder-interface-name clazz))
            (.getName ProtoMapper))}))


(defn emit-mapper [name classes ns]
  (let [type-name    (symbol (str 'ProtoMapper '_ (s/replace *ns* \. \_) '_ name))
        interfaces   (map (partial proto-builder-interface ns) classes)
        sym          (symbol (str *ns*) (str name))
        clazz        (gensym 'clazz)
        this         (gensym 'this)
        bytea        (gensym 'bytea)
        emit-methods (fn [f]
                       (emit-case
                         clazz
                         (map (juxt identity f) classes)
                         `(throw (new IllegalArgumentException (str "unknown " ~clazz)))))]
    `(do
       (defrecord ~type-name []

         ProtoMapper

         (getNamespace [this#] ~ns)

         (getClasses [this#]
           #{ ~@classes })

         ~@(mapcat
             (fn [{:keys [name impl]}]
               (into [name] impl))
             interfaces)

         (~from-bytes-method [~this ~clazz ~bytea]
          ~(emit-methods
             (fn [dep-class]
               `(. ~this
                   ~(builder-interface-from-bytes-method-name dep-class)
                   ~bytea))))

         (~get-transient-method [~this ~clazz]
          ~(emit-methods
             (fn [dep-class]
               `(. ~this
                   ~(builder-interface-get-transient-method-name dep-class)))))

         (~get-proto-method [~this ~clazz]
          ~(emit-methods
             (fn [dep-class]
               `(. ~this
                   ~(builder-interface-get-proto-method-name dep-class))))))
       
       (def ~name (new ~type-name)))))
