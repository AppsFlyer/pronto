(ns pronto.core
  (:require [pronto.wrapper :as w]
            [pronto.emitters :as e]
            [pronto.type-gen :as t]
            [pronto.transformations :as transform]
            [pronto.utils :as u]
            [pronto.protos :refer [global-ns]]
            [pronto.lens :as lens]
            [pronto.schema :as schema]
            [clojure.walk :refer [macroexpand-all]]
            [potemkin]
            [clojure.string :as s])
  (:import [pronto ProtoMap ProtoMapper]
           [com.google.protobuf Message GeneratedMessageV3 ByteString]))

(def ^:dynamic *instrument?* false)

(def ^:private default-values #{0 0.0 nil "" false {} [] (byte-array 0) ByteString/EMPTY})
(def remove-default-values-xf
  (remove (fn [[_ v]] (contains? default-values v))))

(defn- resolve-class [class-sym]
  (when-let [clazz (if (class? class-sym) class-sym (resolve class-sym))]
    (when-not (instance? Class clazz)
      (throw (IllegalArgumentException. (str class-sym " is not a class"))))
    (when-not (.isAssignableFrom Message ^Class clazz)
      (throw (IllegalArgumentException. (str clazz " is not a protobuf class"))))
    clazz))


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
  (.whichOneOf m k))

(defn one-of [^ProtoMap m k]
  (when-let [k' (which-one-of m k)]
    (get m k')))



(defn- with-catch [mapper clazz & body]
  (let [mapper (u/with-type-hint mapper ProtoMapper)]
    `(try
       ~@body
       (catch Exception e#
         ;; If the class was never loaded by the current mapper,
         ;; rethrow with a more descriptive error message.
         ;; We defer this level of validation by piggybacking on the try block,
         ;; in order to avoid this code path which in practice indicates a user error.
         (let [loaded-classes# (.getClasses ~mapper)]
           (if (and ~clazz (get loaded-classes# ~clazz))
             (throw e#)
             (throw (new IllegalArgumentException
                         (str ~clazz " is not loaded by mapper")
                         e#))))))))


(defmacro proto-map [mapper clazz & kvs]
  {:pre [(even? (count kvs))]}
  (let [resolved-class (resolve-class clazz)
        mapper         (e/with-builder-class-hint mapper resolved-class)]
    (with-catch mapper resolved-class
      (if (empty? kvs)
        (if resolved-class
          `(. ~mapper ~(e/builder-interface-get-proto-method-name resolved-class))
          `(. ~mapper ~e/get-proto-method ~clazz))
        (let [chain (map (fn [[k v]] `(assoc ~k ~v)) (partition 2 kvs))]
          `(lens/p-> ~(if resolved-class
                        `(. ~mapper ~(e/builder-interface-get-transient-method-name resolved-class))
                        `(. ~mapper ~e/get-transient-method ~clazz))
                     ~@chain))))))

(defn proto-map? [m]
  (instance? ProtoMap m))

(defmacro clj-map->proto-map [mapper clazz m]
  (let [resolved-class (resolve-class clazz)
        mapper         (e/with-builder-class-hint mapper resolved-class)]
    (with-catch mapper resolved-class
      `(transform/map->proto-map
         ~(if resolved-class
            `(. ~mapper ~(e/builder-interface-get-transient-method-name resolved-class))
            `(. ~mapper ~e/get-transient-method ~clazz))
         ~m))))

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

(defmacro bytes->proto-map [mapper clazz bytes]
  (if-let [resolved-class  (resolve-class clazz)]
    (let [mapper (e/with-builder-class-hint mapper resolved-class)]
      (with-catch mapper clazz
        `(. ~mapper ~(e/builder-interface-from-bytes-method-name resolved-class) ~bytes)))
    `(. ~(u/with-type-hint mapper ProtoMapper)
        ~e/from-bytes-method
        ~clazz ~bytes)))


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



(defn schema [proto-map-or-class & ks]
  (schema/schema
    (cond
      (class? proto-map-or-class)     proto-map-or-class
      (proto-map? proto-map-or-class) (class (proto-map->proto proto-map-or-class)))
    ks))

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

(potemkin/import-vars [pronto.lens
                       p->
                       pcond->
                       clear-field
                       clear-field!
                       assoc-if]
                      [pronto.utils
                       ->kebab-case])
