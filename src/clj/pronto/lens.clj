(ns pronto.lens
  (:refer-clojure
   :exclude [get])
  (:require [pronto.emitters :as e]
            [pronto.utils :as u]
            [pronto.protos :refer [global-ns]])
  (:import [com.google.protobuf GeneratedMessageV3$Builder]
           [pronto ProtoMap TransientProtoMap]))


(defn clear-field [^ProtoMap m k]
  (if (.isMutable m)
    (throw (IllegalAccessError. "cannot clear-field on a transient"))
    (.clearField m k)))

(defn clear-field! [^ProtoMap m k]
  (if-not (.isMutable m)
    (throw (IllegalAccessError. "cannot clear-field! on a non-transient"))
    (.clearField m k)))

(defn assoc-or-else [m k v f]
  (if (some? v)
    (assoc m k v)
    (f m k v)))

(defn assoc-if [m k v]
  (assoc-or-else m k v (fn [m _k _v] m)))


(defn- intf-fqn [intf-name]
  (str (u/javaify global-ns) "." intf-name))

(defmacro rget [m k not-found]
  (if-not (keyword? k)
    `(clojure.core/get ~m ~k)
    (let [m2            (u/with-type-hint 'm2 ProtoMap)
          intf-info     (e/interface-info k)
          intf-name     (e/intf-info->intf-name intf-info)
          val-at-method (e/val-at-intf-name2 intf-info)]
      `(let [~m2 ~m]
         (or
           (when-not (nil? ~m2)
             (~(symbol (str "." val-at-method))
              ~(with-meta m2
                 {:tag (intf-fqn intf-name)})))
           ~not-found)))))


(defn emit-assoc-method [m k v builder]
  (let [intf-info    (e/interface-info k)
        intf-name    (e/intf-info->intf-name intf-info)
        assoc-method (e/assoc-intf-name2 intf-info)]
    `(~(symbol (str "." assoc-method))
      ~(with-meta m
         {:tag (intf-fqn intf-name)})
      ~builder
      ~v)))


(defn emit-empty-method [m k]
  (let [intf-info    (e/interface-info k)
        intf-name    (e/intf-info->intf-name intf-info)
        empty-method (e/empty-intf-name2 intf-info)]
    `(~(symbol (str "." empty-method))
      ~(with-meta m
         {:tag (intf-fqn intf-name)}))))


(defmacro rassoc! [m b k v]
  (if-not (keyword? k)
    `(clojure.core/assoc! ~m ~k ~v)
    (let [intf-info    (e/interface-info k)
          intf-name    (e/intf-info->intf-name intf-info)
          assoc-method (e/assoc-intf-name2 intf-info)]
      `(~(symbol (str "." assoc-method))
        ~(with-meta m
           {:tag (intf-fqn intf-name)})
        ~(u/with-type-hint
           b
           GeneratedMessageV3$Builder)
        ~v))))


(defmacro transform-in [m kvs]
  (let [m          (u/with-type-hint m ProtoMap)
        m2         (u/with-type-hint (gensym 'm2) TransientProtoMap)
        new-submap (gensym 'newsubmap)
        submap     (gensym 'submap)
        kv-forest  (u/kv-forest kvs)
        builder    (u/with-type-hint
                     (gensym 'builder)
                     GeneratedMessageV3$Builder)]
    `(let [~m2                  (if (.isMutable ~m) ~m (transient ~m))
           was-in-transaction?# (.isInTransaction ~m2)
           ~builder             (.pmap_getBuilder ~m2)]
       (.setInTransaction ~m2 true)
       ~@(doall
          (for [[k vs] kv-forest
                v      (partition-by u/leaf? vs)]
            (if (u/leaf? (first v))
              `(do
                 ~@(doall
                    (for [leaf v]
                      (let [val-fn (eval (u/leaf-val leaf))]
                        (val-fn m2 builder k)))))
              `(let [~submap     (or (rget ~m2 ~k nil)
                                     ~(emit-empty-method m2 k))
                     ~new-submap (pronto.lens/transform-in ~submap ~(u/flatten-forest v))]
                 (rassoc! ~m2 ~builder ~k ~new-submap)))))
       (if was-in-transaction?#
         ~m2
         (persistent! ~m2)))))


(defn- assoc-transform-kvs [rewrite-fn kvs]
  (->> kvs
       (partition 2)
       (map
         (fn [[ks v]]
           [ks
            (fn [msym bsym k]
              (rewrite-fn
                `(rassoc!
                   ~msym ~bsym ~k ~v)))]))))


(defn- update-transform-kv [rewrite-fn ks f args]
  [[ks
    (fn [msym bsym k]
      (rewrite-fn
        `(let [x# (rget ~msym ~k nil)
               x# (~f x# ~@args)]
           (rassoc! ~msym ~bsym ~k x#))))]])


(defn- clear-field-transform-kv [rewrite-fn k]
  [[[k]
    (fn [msym bsym k]
      (rewrite-fn
        (let [intf-info    (e/interface-info k)
              intf-name    (e/intf-info->intf-name intf-info)
              clear-method (e/clear-intf-name2 intf-info)]
          `(~(symbol (str "." clear-method))
            ~(with-meta msym
               {:tag (intf-fqn intf-name)})
            ~(u/with-type-hint
               bsym
               GeneratedMessageV3$Builder)))))]])

(defn- assoc-if-transform-kvs [rewrite-fn k v]
  [[[k]
    (fn [msym bsym k]
      (let [v2 (gensym 'v)]
        (rewrite-fn
          `(let [~v2 ~v]
             (if (some? ~v2)
               (rassoc! ~msym ~bsym ~k ~v2)
               ~msym)))))]])

(defn- assoc-args->assoc-in-args [k v kvs]
  (->> kvs
       (cons v)
       (cons k)
       (partition 2)
       (mapcat
         (fn [[k v]]
           [[k] v]))))


(defn- fn-name [form]
  (when (coll? form)
    (when-let [fst (first form)]
      (when (symbol? fst)
        (symbol (name fst))))))


(def ^:private update?
  (comp #{'update 'update-in} fn-name))


(def ^:private assoc?
  (comp #{'assoc 'assoc-in 'assoc-if 'assoc-or-else} fn-name))


(def ^:private clear?
  (comp #{'clear-field} fn-name))


(defn- transient-proto-fn? [form]
  (let [fn-name (fn-name form)
        v       (u/safe-resolve fn-name)
        m       (meta v)]
    (->> m
         :arglists
         (some #(when (= (count form) (count %)) %))
         first
         meta
         :transient-proto
         boolean)))


(defn- transformation? [form]
  (or
    (assoc? form)
    (update? form)
    (transient-proto-fn? form)
    (clear? form)))


(defn- rewrite-transformation [g transforms]
  `(transform-in
     ~g
     ~(mapcat
        (fn [form]
          (let [rewrite-fn (let [pred (-> form meta :predicate)]
                             (if (some? pred)
                               (fn [form]
                                 `(when ~pred
                                    ~form))
                               identity))]
            (case (name (fn-name form))
              "assoc"
              (let [[_ k v & kvs] form]
                (assoc-transform-kvs
                  rewrite-fn
                  (assoc-args->assoc-in-args k v kvs)))
              "assoc-in"
              (let [[_ [k & ks] v] form]
                (assoc-transform-kvs
                  rewrite-fn
                  [(into [k] ks) v]))
              "assoc-if"
              (let [[_ k v] form]
                (assoc-if-transform-kvs
                  rewrite-fn
                  k v))
              "update"
              (let [[_ k f & args] form]
                (update-transform-kv rewrite-fn [k] f args))
              "update-in"
              (let [[_ ks f & args] form]
                (update-transform-kv rewrite-fn ks f args))
              "clear-field"
              (let [[_ k] form]
                (clear-field-transform-kv rewrite-fn k))
              (let [[f & args] form]
                [[[(keyword (gensym))]
                  (fn [msym _bsym _]
                    (rewrite-fn
                      (if args
                        `(~f ~msym ~@args)
                        `(~f ~msym))))]]))))
        transforms)))


(defn- rewrite-forms [g forms]
  (->> forms
       (partition-by
         (fn [form]
           (if (transformation? form)
             ::transformation
             form)))
       (map
         (fn [subform]
           (let [x         (first subform)
                 predicate (-> x meta :predicate)
                 wrap-pred (fn [form]
                             (if (nil? predicate)
                               form
                               `(if ~predicate
                                  ~form
                                  ~g)))]
             (if (and (coll? subform)
                      (transformation? x))
               (rewrite-transformation g subform)
               (wrap-pred
                 (cond
                   (keyword? x)            `(rget ~g ~x ~(second subform))
                   (= 'get (fn-name x))    `(p-> ~g ~(second x))
                   (= 'get-in (fn-name x)) `(p-> ~g ~@(second x))
                   :else                   `(-> ~g ~x)))))))))


(defmacro p-> [x & forms]
  (let [g (gensym)]
    `(clojure.core/as-> ~x ~g
       ~@(rewrite-forms g forms))))


(defmacro pcond-> [expr & clauses]
  (assert (even? (count clauses)))
  (let [clauses' (->> clauses
                      (partition 2)
                      (map
                        (fn [[test form]]
                          (let [form (if (keyword? form)
                                       `(rget ~form nil)
                                       form)]
                            (vary-meta
                              form
                              assoc
                              :predicate
                              test)
                            ))))]
    `(p->
       ~expr
       ~@clauses')))


