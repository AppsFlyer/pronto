(ns pronto.core
  (:require [pronto.utils :as u]
            [pronto.wrapper :as w]
            [pronto.emitters :as e]
            [pronto.type-gen :as t]
            [pronto.proto :as proto]))


(def get-proto proto/get-proto)

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

(defn emit [^Class clazz]
  `(do
     (declare ~(u/map-ctor-name clazz))
     (declare ~(u/proto-ctor-name clazz))
     (declare ~(u/transient-ctor-name clazz))

     ~(e/emit-deftype clazz)
     ~(e/emit-transient clazz)
     ~(e/emit-proto-ctor clazz)
     ~(e/emit-map-ctor clazz)))


(def loaded-classes (atom #{}))

(defn unload-classes! [] (swap! loaded-classes empty))

(defmacro defproto [class-sym]
  (let [^Class clazz (if-not (symbol? class-sym)
                       (throw (IllegalArgumentException. (str "defproto: expected a class, got " (class class-sym))))
                       (resolve class-sym))]
    (if (nil? clazz)
      (throw (IllegalArgumentException. (str "defproto: cannot resolve class " class-sym)))
      (let [deps      (reverse (resolve-deps clazz))
            class-key [*ns* clazz]]
        (when (not (get @loaded-classes class-key))
          (swap! loaded-classes conj class-key)
          `(do
             ~@(for [dep deps]
                 (emit dep))

             ~(emit clazz)))))))

