(ns pronto.core
  (:require [pronto.wrapper :as w]
            [pronto.emitters :as e]
            [pronto.type-gen :as t]
            [pronto.transformations :as transform]
            [pronto.utils :as u]
            [pronto.protos :refer [global-ns]]
            [pronto.runtime :as r]
            [pronto.reflection :as reflect]
            [clojure.walk :refer [macroexpand-all]]
            [potemkin]
            [clojure.string :as s])
  (:import [pronto ProtoMap ProtoMapper]
           [com.google.protobuf Message GeneratedMessageV3
            Descriptors$FieldDescriptor
            Descriptors$Descriptor ByteString]))

(def ^:dynamic *instrument?* false)

(def ^:private default-values #{0 0.0 nil "" false {} [] (byte-array 0) ByteString/EMPTY})
(def remove-default-values-xf
  (remove (fn [[_ v]] (contains? default-values v))))

(defn- resolve-class [class-sym]
  (let [clazz (if (class? class-sym) class-sym (resolve class-sym))]
    (when-not clazz
      (throw (IllegalArgumentException. (str "Cannot resolve \"" class-sym "\". Did you forget to import it?"))))
    (when-not (instance? Class clazz)
      (throw (IllegalArgumentException. (str class-sym " is not a class"))))
    (when-not (.isAssignableFrom Message ^Class clazz)
      (throw (IllegalArgumentException. (str clazz " is not a protobuf class"))))
    clazz))

(defn- resolve-loaded-class-safely [class-sym ^ProtoMapper mapper]
  (let [clazz (resolve-class class-sym)]
    (when (reflect/class-defined?
           (str
            (u/javaify (.getNamespace mapper))
            "."
            (u/class->map-class-name clazz)))
      clazz)))

(defn- resolve-loaded-class [class-sym mapper]
  (if-let [c (resolve-loaded-class-safely class-sym mapper)]
    c
    (throw (IllegalArgumentException. (str class-sym " not loaded")))))

(defn disable-instrumentation! []
  (alter-var-root #'*instrument?* (constantly false)))

(defn enable-instrumentation! []
  (alter-var-root #'*instrument?* (constantly true)))

(defn proto-map->proto
  "Returns the protobuf instance associated with the proto-map"
  [^ProtoMap m]
  (.pmap_getProto m))

(defn has-field? [^ProtoMap m k]
  (.pmap_hasField m k))

(defn which-one-of [^ProtoMap m k]
  (.pmap_whichOneOf m k))

(defn one-of [^ProtoMap m k]
  (when-let [k' (which-one-of m k)]
    (get m k')))

(defn- resolve-mapper [mapper-sym]
  (if (instance? ProtoMapper mapper-sym)
    mapper-sym
    @(resolve mapper-sym)))

(defmacro proto-map [mapper clazz & kvs]
  {:pre [(even? (count kvs))]}
  (let [clazz  (resolve-loaded-class clazz (resolve-mapper mapper))
        mapper (with-meta
                 mapper
                 {:tag (str (u/javaify global-ns) "." (e/builder-interface-name clazz))})]
    (if (empty? kvs)
      `(. ~mapper ~(e/builder-interface-get-proto-method-name clazz))
      (let [chain (map (fn [[k v]] `(assoc ~k ~v)) (partition 2 kvs))]
        `(r/p-> (. ~mapper ~(e/builder-interface-get-transient-method-name clazz))
                ~@chain)))))

(defn proto-map? [m]
  (instance? ProtoMap m))

(defmacro clj-map->proto-map [mapper clazz m]
  (let [clazz  (resolve-loaded-class clazz (resolve-mapper mapper))
        mapper (with-meta mapper {:tag (str (u/javaify global-ns) "." (e/builder-interface-name clazz))})]
    `(transform/map->proto-map
      (. ~mapper ~(e/builder-interface-get-transient-method-name clazz))
      ~m)))

(defn proto->proto-map [mapper proto]
  (e/proto->proto-map proto mapper))

(defn proto-map->clj-map
  ([proto-map] (proto-map->clj-map proto-map (map identity)))
  ([proto-map xform]
   (let [mapper
         (map
          (fn [[k v]]
            [k (cond
                 (proto-map? v) (proto-map->clj-map v xform)
                 (coll? v)      (let [fst (first v)]
                                  (if (proto-map? fst)
                                    (into []
                                          (map #(proto-map->clj-map % xform))
                                          v)
                                    v))
                 :else          v)]))
         xform  (comp mapper xform)]
     (into {}
           xform
           proto-map))))

(defmacro bytes->proto-map [mapper ^Class clazz ^bytes bytes]
  (let [^ProtoMapper mapper (resolve-mapper mapper)
        clazz               (resolve-loaded-class clazz mapper)
        bytea               (with-meta bytes {:tag "[B"})]
    `(. ~mapper ~(e/builder-interface-from-proto-method-name clazz)
        (~(u/static-call clazz "parseFrom")
         ~bytea))))

(defn byte-mapper [mapper ^Class clazz]
  (let [msym (.getSym ^ProtoMapper mapper)
        csym (symbol (.getName clazz))]
    (eval
     `(fn [bytes#]
        (bytes->proto-map ~msym ~csym bytes#)))))

(defn proto-map->bytes [proto-map]
  (.toByteArray ^GeneratedMessageV3 (proto-map->proto proto-map)))

(defn remap
  "Remaps `proto-map` using `mapper`.
  The returned proto-map is subject to the configuration of the new mapper."
  [mapper proto-map]
  (.remap ^ProtoMap proto-map mapper))

(defn- resolve-deps
  ([ctx ^Class clazz] (first (resolve-deps ctx clazz #{})))
  ([ctx ^Class clazz seen-classes]
   (let [fields       (t/get-fields clazz ctx)
         deps-classes (->> fields
                           (map #(t/get-class (:type-gen %)))
                           (filter (fn [^Class clazz]
                                     (and (not (.isEnum clazz))
                                          (not (w/protobuf-scalar? clazz))))))
         seen-classes (conj seen-classes clazz)]
     (reduce (fn [[deps seen :as acc] dep-class]
               (if (get seen dep-class)
                 acc
                 (let [new-deps (conj deps dep-class)
                       [x y]    (resolve-deps ctx dep-class seen-classes)]
                   [(into new-deps x) y])))
             [[] seen-classes]
             deps-classes))))

(defn- update' [m k f]
  (if-let [v (get m k)]
    (assoc m k (f v))
    m))

(defn- resolve' [s]
  (if (symbol? s)
    (resolve s)
    s))


(defn- type-info [^Class clazz]
  (cond
    (reflect/enum? clazz) (into #{} (map str) (reflect/enum-values clazz))
    :else                 clazz))


(defn- field-schema [^Class clazz ^Descriptors$FieldDescriptor descriptor]
  (cond
    (.isMapField descriptor) (let [{:keys [key-type val-type]} (t/map-type-info clazz descriptor)]
                               {(type-info key-type)
                                (type-info val-type)})
    (.isRepeated descriptor) [(type-info (t/repeated-type-info clazz descriptor))]
    :else                    (let [^Class x (t/field-type clazz descriptor)]
                               (type-info x))))


(defn- find-descriptors [clazz descriptors ks]
  (loop [clazz       clazz
         descriptors descriptors
         ks          ks]
    (if-not (seq ks)
      [clazz descriptors]
      (let [[k & ks] ks
            ^Descriptors$FieldDescriptor descriptor
            (some
             (fn [^Descriptors$FieldDescriptor d]
               (when (= (name k) (.getName d))
                 d))
             descriptors)]
        (when descriptor
          (let [sub-descs (.getFields ^Descriptors$Descriptor (.getMessageType descriptor))
                clazz     (t/field-type clazz descriptor)]
            (recur clazz sub-descs ks)))))))

(defn schema [proto-map-or-class & ks]
  (let [clazz               (cond
                              (class? proto-map-or-class)     proto-map-or-class
                              (proto-map? proto-map-or-class) (class (proto-map->proto proto-map-or-class)))
        [clazz descriptors] (find-descriptors
                             clazz
                             (map :fd (t/get-fields clazz {}))
                             ks)]
    (when descriptors
      (into {}
            (map
             (fn [^Descriptors$FieldDescriptor fd]
               [(keyword
                 (when-let [oneof (.getContainingOneof fd)]
                   (.getName oneof))
                 (.getName fd))
                (field-schema clazz fd)]))
            descriptors))))

(defn- init-ctx [opts]
  (merge
   {:key-name-fn   identity
    :enum-value-fn identity
    :iter-xf       nil
    :instrument?   *instrument?*}
   (-> (apply hash-map opts)
       (update' :key-name-fn eval)
       (update' :enum-value-fn eval)
       (update' :iter-xf resolve')
       (update' :encoders
                #(into
                  {}
                  (map
                   (fn [[k v]]
                     (let [resolved-k
                           (cond-> k
                             (symbol? k) resolve)]
                       [resolved-k v])))
                  (eval %))))))


(defn dependencies [^Class clazz]
  (set (resolve-deps (init-ctx nil) clazz)))


(defn depends-on? [^Class dependent ^Class dependency]
  (boolean (get (dependencies dependent) dependency)))


(defn- proto-ns-name [mapper-sym-name]
  (s/join "." [global-ns *ns* mapper-sym-name]))


(defmacro defmapper [name classes & opts]
  {:pre [(symbol? name)
         (vector? classes)
         (not-empty classes)
         (even? (count opts))]}
  (let [resolved-classes (mapv resolve classes)]
    (when (some nil? resolved-classes)
      (throw (ex-info "Cannot resolve classes" {:classes classes})))
    (let [ctx           (init-ctx opts)
          proto-ns-name (proto-ns-name name)
          ctx           (assoc ctx :ns proto-ns-name)
          sub-deps      (->> resolved-classes
                             (mapcat (partial resolve-deps ctx))
                             reverse)
          deps          (distinct (concat sub-deps resolved-classes))]
      `(do
         (u/with-ns ~proto-ns-name
           ~(e/emit-decls deps)
           ~@(doall
               (for [dep deps]
                 (e/emit-proto-map dep ctx))))

         ~(e/emit-mapper name deps proto-ns-name)))))

(defn macroexpand-class [^Class clazz]
  (macroexpand-all `(defmapper abc [~(symbol (.getName clazz))])))

(potemkin/import-vars [pronto.runtime
                       p->
                       pcond->
                       clear-field
                       clear-field!
                       assoc-if]
                      [pronto.utils
                       ->kebab-case])
