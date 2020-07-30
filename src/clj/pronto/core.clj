(ns pronto.core
  (:require [pronto.utils :as u]
            [pronto.wrapper :as w]
            [pronto.emitters :as e]
            [pronto.type-gen :as t])
  (:import [pronto ProtoMap]))

(defn get-proto [^ProtoMap m]
  (.getProto m))

(defn clear-field [^ProtoMap m k]
  (if (.isMutable m)
    (throw (IllegalAccessError. "cannot clear-field on a transient"))
    (.clearField m k)))

(defn clear-field! [^ProtoMap m k]
  (if-not (.isMutable m)
    (throw (IllegalAccessError. "cannot clear-field! on a non-transient"))
    (.clearField m k)))

(defn has-field? [^ProtoMap m k]
  (.hasField m k))

(defn which-one-of [^ProtoMap m k]
  (.whichOneOf m k))

(defn one-of [^ProtoMap m k]
  (when-let [k' (which-one-of m k)]
    (get m k')))

(defn resolve-deps
  ([^Class clazz] (first (resolve-deps clazz #{})))
  ([^Class clazz seen-classes]
   (let [fields       (t/get-fields clazz)
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
                       [x y]    (resolve-deps dep-class seen-classes)]
                   [(into new-deps x) y])))
             [[] seen-classes]
             deps-classes))))


(def ^:private loaded-classes (atom #{}))

(defn unload-classes! [] (swap! loaded-classes empty))

(defmacro defproto [class-sym]
  (let [^Class clazz (if-not (symbol? class-sym)
                       (throw (IllegalArgumentException. (str "defproto: expected a class, got " (class class-sym))))
                       (resolve class-sym))]
    (if (nil? clazz)
      (throw (IllegalArgumentException. (str "defproto: cannot resolve class " class-sym)))
      (let [map-class-name (u/class->map-class-name clazz)
            deps           (reverse (resolve-deps clazz))
            class-key      [*ns* clazz]]
        (if (get @loaded-classes class-key)
          map-class-name
          (do
            (swap! loaded-classes conj class-key)
            `(do
               ~@(for [dep deps]
                   (e/emit-proto-map dep))

               ~(e/emit-proto-map clazz)

               ~map-class-name)))))))


(comment
  (import '(protogen.generated People$Person))

  (defproto People$Person))
