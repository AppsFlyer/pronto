(ns pronto.core
  (:require [pronto.wrapper :as w]
            [pronto.emitters :as e]
            [pronto.type-gen :as t]
            [pronto.transformations :as transform]
            [pronto.utils :as u]
            [pronto.protos]
            [pronto.runtime]
            [clojure.walk :refer [macroexpand-all]]
            [potemkin])
  (:import [pronto ProtoMap]
           [com.google.protobuf Message GeneratedMessageV3]))

(def ^:private loaded-classes (atom {}))

(def ^:dynamic *instrument?* false)

(def ^:private global-ns "pronto.protos")

(defn- resolve-class [class-sym]
  (let [clazz (resolve class-sym)]
    (when-not clazz
      (throw (IllegalArgumentException. (str "Cannot resolve \"" class-sym "\". Did you forget to import it?"))))
    (when-not (instance? Class clazz)
      (throw (IllegalArgumentException. (str class-sym " is not a class"))))
    (when-not (.isAssignableFrom Message ^Class clazz)
      (throw (IllegalArgumentException. (str clazz " is not a protobuf class"))))
    clazz))

(defn- resolve-loaded-class [class-sym]
  (let [clazz (resolve-class class-sym)]
    (if (get @loaded-classes clazz)
      clazz
      (throw (IllegalArgumentException. (str clazz " not loaded"))))))

(defn disable-instrumentation! []
  (alter-var-root #'*instrument?* (constantly false)))

(defn enable-instrumentation! []
  (alter-var-root #'*instrument?* (constantly true)))

(defn proto-map->proto
  "Returns the protobuf instance associated with the proto-map"
  [^ProtoMap m]
  (.pmap_getProto m))

(defn clear-field [^ProtoMap m k]
  (if (.pmap_isMutable m)
    (throw (IllegalAccessError. "cannot clear-field on a transient"))
    (.pmap_clearField m k)))

(defn clear-field! [^ProtoMap m k]
  (if-not (.pmap_isMutable m)
    (throw (IllegalAccessError. "cannot clear-field! on a non-transient"))
    (.pmap_clearField m k)))

(defn has-field? [^ProtoMap m k]
  (.pmap_hasField m k))

(defn which-one-of [^ProtoMap m k]
  (.pmap_whichOneOf m k))

(defn one-of [^ProtoMap m k]
  (when-let [k' (which-one-of m k)]
    (get m k')))

(defmacro proto-map [clazz & kvs]
  (let [clazz (resolve-loaded-class clazz)]
    (if (empty? kvs)
      (symbol global-ns (str (e/empty-map-var-name clazz)))
      (let [chain# (map (fn [[k v]] `(assoc! ~k ~v)) (partition 2 kvs))]
        `(-> ~(e/emit-default-transient-ctor clazz global-ns)
             ~@chain#
             persistent!)))))

(defmacro clj-map->proto-map [clazz m]
  (let [clazz (resolve-loaded-class clazz)]
    `(transform/map->proto-map
       ~(e/emit-default-transient-ctor clazz global-ns)
       ~m)))

(defn proto->proto-map [^GeneratedMessageV3 proto]
  (e/proto->proto-map proto))


(defn proto-map->clj-map
  ([proto-map] (proto-map->clj-map proto-map (map identity)))
  ([proto-map xform]
   (let [mapper (map (fn [[k v]]
                       [k (if (instance? ProtoMap v)
                            (proto-map->clj-map v)
                            v)]))
         xform  (comp mapper xform)]
     (into {}
           xform
           proto-map))))

(defmacro bytes->proto-map [^Class clazz ^bytes bytes]
  (let [clazz (resolve-loaded-class clazz)
        bytea (with-meta bytes {:tag "[B"})]
    `(~(symbol
         global-ns
         (str '-> (u/class->map-class-name clazz)))
      (~(u/static-call clazz "parseFrom")
       ~bytea) nil)))

(defn proto-map->bytes [proto-map]
  (.toByteArray ^GeneratedMessageV3 (proto-map->proto proto-map)))

(defn- resolve-deps
  ([^Class clazz ctx] (first (resolve-deps clazz #{} ctx)))
  ([^Class clazz seen-classes ctx]
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
                       [x y]    (resolve-deps dep-class seen-classes ctx)]
                   [(into new-deps x) y])))
             [[] seen-classes]
             deps-classes))))


(defn- update' [m k f]
  (if-let [v (get m k)]
    (assoc m k (f v))
    m))

(defn- init-ctx [opts]
  (merge {:key-name-fn   identity
          :enum-value-fn identity
          :ns            "pronto.protos"
          :instrument?   *instrument?*}
         (-> (apply hash-map opts)
             (update' :key-name-fn eval)
             (update' :enum-value-fn eval)
             (update' :encoders #(into {}
                                       (map (fn [[k v]]
                                              (let [resolved-k
                                                    (cond-> k
                                                      (symbol? k) (resolve))]
                                                [resolved-k v])))
                                       (eval %))))))

(defn dependencies [^Class clazz]
  (set (resolve-deps clazz (init-ctx nil))))

(defn depends-on? [^Class dependent ^Class dependency]
  (boolean (get (dependencies dependent) dependency)))


(defn proto-map? [m]
  (instance? ProtoMap m))

(defn unload-classes! [] (swap! loaded-classes empty))


(defn- emit-proto-map [^Class clazz ctx]
  (when-not (get @loaded-classes clazz)
    (swap! loaded-classes assoc clazz (str *ns*))
    (e/emit-proto-map clazz ctx)))


(defmacro defproto [class & opts]
  (let [ctx          (init-ctx opts)
        ^Class clazz (resolve-class class)
        deps         (reverse (resolve-deps clazz ctx))]
    `(u/with-ns "pronto.protos"
       ~@(doall
           (for [dep deps]
             (emit-proto-map dep ctx)))

       ~(emit-proto-map clazz ctx))))


(defn macroexpand-class [^Class clazz]
  (macroexpand-all `(defproto ~(symbol (.getName clazz)))))


(potemkin/import-vars [pronto.runtime p->])
