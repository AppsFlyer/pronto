(ns pronto.utils
  (:require [clojure.string :as s])
  (:import
   [java.lang.reflect Field]
   [clojure.lang Symbol]
   [com.google.protobuf
    Descriptors$FieldDescriptor
    Descriptors$GenericDescriptor
    Descriptors$FieldDescriptor$Type]))


(defn sanitized-class-name [^Class clazz]
  ;; TODO: did I reimplement a crappier .getSimpleName?
  (let [package-name (.getName (.getPackage clazz))
        class-name   (.getName clazz)]
    (subs class-name (inc (count package-name)))))

(defn class->map-class-name [^Class clazz]
  (symbol (str (sanitized-class-name clazz) "ProtoMap")))

(defn class->abstract-map-class-name [^Class clazz]
  (symbol (str (sanitized-class-name clazz) "AbstractMap")))

(defn class->abstract-persistent-map-class-name [^Class clazz]
  (symbol (str (sanitized-class-name clazz) "AbstractPersistentMap")))



(defn class->transient-class-name [^Class clazz]
  (symbol (str 'transient- (sanitized-class-name clazz))))


(defn ->kebab-case [^String s]
  (when s
    (s/lower-case (.replace s \_ \-))))

(defn with-type-hint [sym ^Class clazz]
  (with-meta sym {:tag (symbol (.getName clazz))}))

(defn ctor-name [prefix ^Class clazz]
  (symbol (str prefix '-> (class->map-class-name clazz))))

(defn ->camel-case
  "Implements protobuf's camel case conversion for Java. See: https://github.com/protocolbuffers/protobuf/blob/v3.12.4/src/google/protobuf/compiler/java/java_helpers.cc#L157"
  [^String s]
  (when-let [length (some-> s .length)]
    (loop [i                 0
           ^StringBuilder sb (StringBuilder.)
           cap-next-letter?  true]
      (if (= i length)
        (.toString sb)
        (let [x (.charAt s i)]
          (cond
            (Character/isLowerCase x)
            (recur (inc i)
                   (.append sb (if cap-next-letter? (Character/toUpperCase x) x))
                   false)
            (Character/isUpperCase x)
            (recur (inc i) (.append sb x) false)
            (Character/isDigit x)
            (recur (inc i) (.append sb x) true)
            :else
            (recur (inc i) sb true)))))))

(defn field->camel-case [^Descriptors$GenericDescriptor field]
  (->camel-case (.getName field)))

(defn field->kebab-case [^Descriptors$GenericDescriptor field]
  (->kebab-case (.getName field)))

(defn message? [^Descriptors$FieldDescriptor fd]
  (= (.getType fd)
     Descriptors$FieldDescriptor$Type/MESSAGE))

(defn enum? [^Descriptors$FieldDescriptor fd]
  (= (.getType fd)
     Descriptors$FieldDescriptor$Type/ENUM))

(defn static-call [^Class class method-name]
  (symbol (str (.getName class) "/" method-name)))


(defn- type-error-info [error-type ^Class clazz field-name expected-type value]
  {:error         error-type
   :class         clazz
   :field         (->kebab-case (name field-name))
   :expected-type expected-type
   :actual-type   (type value)
   :value         value})

(defn make-type-error [^Class clazz field-name expected-type value]
  (ex-info "Invalid type"
           (type-error-info :invalid-type clazz field-name expected-type value)))

(defn make-enum-error [^Class clazz field-name expected-type value]
  (ex-info "Invalid enum value"
           (type-error-info :invalid-enum-value
                            clazz field-name expected-type value)))


(defn implode [[x & xs]]
  (cond (not x)  []
        (not xs) [x]
        :else    [x (implode xs)]))


(defmacro with-ns [new-ns & body]
  (let [orig-ns          *ns*
        orig-ns-name     (ns-name orig-ns)
        ns-name-sym      (symbol new-ns)
        existing-classes (set (when-let [n (find-ns ns-name-sym)]
                                (vals (ns-imports n))))]
    `(do
       (in-ns (quote ~ns-name-sym))
       ~@(for [[^Symbol class-sym ^Class clazz]
               (ns-imports orig-ns)
               :let  [class-name (.getName clazz)
                      package-prefix (subs class-name 0
                                           (- (count class-name)
                                              (count (name class-sym))
                                              1))]
               :when (not (get existing-classes clazz))]
           `(import '[~(symbol package-prefix) ~class-sym]))
       ~@body
       #_(finally)
       (in-ns (quote ~(symbol orig-ns-name))))))


(defmacro ... [f o]
  (let [t (gensym 't)]
    `(let [~t ~o]
       (try
         (~(symbol (str "." f)) ~t)
         (catch IllegalArgumentException ~'_
           (let [^Field rf# (.getDeclaredField (class ~t) ~(str f))]
             (.setAccessible rf# true)
             (.get rf# ~t)))))))



(defn- split' [f coll]
  (loop [[x & xs :as c] coll
         res            []]
    (if-not x
      res
      (if (f x)
        (recur
          xs
          (conj res x))
        (let [[a b] (split-with (complement f) c)]
          (recur
            b
            (conj res a)))))))


(def leaf-val :val)

(defn leaf [x] (with-meta {:val x} {::leaf? true}))

(def leaf? (comp boolean ::leaf? meta))


(defn kv-forest [kvs]
  (loop [[kv-partition & ps] (partition-by ffirst kvs)
         res                 []]
    (if-not kv-partition
      res
      (let [leader-key   (first (ffirst kv-partition))
            follower-kvs (->> kv-partition
                              (map
                                (fn [[ks v]]
                                  (let [rks (rest ks)]
                                    (if (seq rks)
                                      (vector rks v)
                                      (leaf v)))))
                              (split' leaf?))]
        (recur
          ps
          (conj
            res
            [leader-key
             (mapcat
               (fn [g]
                 (if (leaf? g)
                   [g]
                   (kv-forest g)))
               follower-kvs)]))))))


(defn- flatten-forest* [forest]
  (if-not (seq forest)
    []
    (for [[k tree] forest
          v        tree]
      (if (leaf? v)
        [[k] (leaf-val v)]
        (mapcat
          (fn [[k' v']]
            [(cons k k') v'])
          (flatten-forest* [v]))))))


(defn flatten-forest [forest]
  (partition 2 (apply concat (flatten-forest* forest))))


(defn safe-resolve [x]
  (try
    (resolve x)
    (catch Exception _)))
