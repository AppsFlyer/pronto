(ns pronto.wrapper
  (:require [pronto.utils :as u])
  (:import [clojure.lang Reflector]
           [com.google.protobuf ByteString]))

(defprotocol Wrapper
  (wrap [this v])
  (unwrap [this v]))

(def numeric-scalar?
  (comp boolean #{Integer/TYPE Long/TYPE Double/TYPE Float/TYPE}))

(defn protobuf-scalar? [^Class clazz]
  (boolean (or (numeric-scalar? clazz)
               (#{Boolean/TYPE String ByteString} clazz))))


(defmulti gen-wrapper
  (fn [^Class clazz]
    (cond
      (.isEnum clazz)                :enum
      (= ByteString clazz)           :bytes
      (not (protobuf-scalar? clazz)) :message
      :else                          :scalar)))

(defmethod gen-wrapper
  :enum
  [^Class clazz]
  (let [values   (Reflector/invokeStaticMethod clazz "values" (to-array nil))
        enum->kw (map-indexed #(vector %1 (keyword (u/->kebab-case (.name %2))))
                              values)
        kw->enum (map #(vector (keyword (u/->kebab-case (.name %1)))
                               (symbol (str (.getName clazz) "/" (.name %1))))
                      values)]

    (reify Wrapper
      (wrap [_ v]
        `(case (.ordinal ~v)
           ~@(interleave
               (map first enum->kw)
               (map second enum->kw))
           (throw (IllegalArgumentException. (str "can't wrap " ~v)))))

      (unwrap [_ v]
        `(case ~v
           ~@(interleave
               (map first kw->enum)
               (map second kw->enum))
           (throw (IllegalArgumentException. (str "can't unwrap " ~v))))))))


(defn make-error-message [expected-class value]
  (str "expected class " expected-class ", but got " (or (class value) "nil")))

(defmethod gen-wrapper
  :message
  [^Class clazz]
  (let [wrapper-type (u/class-name->wrapper-class-name clazz)]
    (reify Wrapper
      (wrap [_ v]
        `(new ~wrapper-type ~v))

      (unwrap [_ v]
        `(cond
           (= (class ~v) ~clazz) ~v

           (= (class ~v) ~wrapper-type)
           ~(let [u (with-meta (gensym 'u) {:tag 'pronto.wrapper/ProtoMap})]
              `(let [~u ~v]
                 (pronto.proto/get-proto ~u)))

           (map? ~v)
           ;; TODO: duplicate code
           ~(let [u (with-meta (gensym 'u) {:tag 'pronto.wrapper/ProtoMap})]
              `(let [~u (~(u/ctor-name clazz) ~v)]
                 (pronto.proto/get-proto ~u)))

           :else (throw (IllegalArgumentException. (make-error-message ~clazz ~v))))))))


;; TODO: Get rid of this method, collapse into regular scalar
(defmethod gen-wrapper
  :bytes
  [_]
  ;; the class must be `ByteString`
  (reify Wrapper
    (wrap [_ v]
      v)

    (unwrap [_ v]
      `(if (instance? com.google.protobuf.ByteString ~v)
         ~v
         (throw (IllegalArgumentException. (make-error-message com.google.protobuf.ByteString ~v)))))))


(defmethod gen-wrapper
  :scalar
  [^Class clazz]
  (let [wrapper-type (u/class-name->wrapper-class-name clazz)]
    (reify Wrapper
      (wrap [_ v] v)

      (unwrap [_ v]
        ;; TODO: clean this up...
        (cond
          (= String clazz)
          `(if-not (= String (class ~v))
             (throw (IllegalArgumentException. (make-error-message ~clazz ~v)))
             ~v)
          (= Boolean/TYPE clazz)
          `(if-not (= Boolean (class ~v))
             (throw (IllegalArgumentException. (make-error-message ~clazz ~v)))
             ~v)
          (numeric-scalar? clazz)
          (let [vn (u/with-type-hint v Number)]
            `(if-not (instance? Number ~vn)
               (throw (IllegalArgumentException. (make-error-message ~clazz ~v)))
               (let [~vn ~v]
                 (~(symbol (str "." (str clazz) "Value")) ~vn))))
          :else (throw (IllegalArgumentException. (make-error-message ~clazz ~v))))))))

