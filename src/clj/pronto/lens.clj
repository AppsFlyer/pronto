(ns pronto.lens
  (:require [pronto.utils :as u])
  (:import [pronto ProtoMap TransientProtoMap]))


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


(defmacro transform-in [m kvs]
  (let [m          (u/with-type-hint m ProtoMap)
        m2         (u/with-type-hint (gensym 'm2) TransientProtoMap)
        new-submap (gensym 'newsubmap)
        submap     (gensym 'submap)
        pmap?      (gensym 'pmap?)
        kv-forest  (u/kv-forest kvs)]
    `(let [~pmap?               (u/proto-map? ~m)
           ~m2                  (if (and ~pmap? (.isMutable ~m)) ~m (transient ~m))
           was-in-transaction?# (and ~pmap? (.isInTransaction ~m2))]
       (when ~pmap?
         (.setInTransaction ~m2 true))
       ~@(doall
          (for [[k vs] kv-forest
                v      (partition-by u/leaf? vs)]
            (if (u/leaf? (first v))
              `(do
                 ~@(doall
                    (for [leaf v]
                      (let [val-fn (eval (u/leaf-val leaf))]
                        (val-fn m2 k)))))
              `(let [~submap     (or (get ~m2 ~k nil)
                                     (when ~pmap?
                                       (.empty ~m2 ~k)))
                     ~new-submap (pronto.lens/transform-in ~submap ~(u/flatten-forest v))]
                 (assoc! ~m2 ~k ~new-submap)))))
       (if was-in-transaction?#
         ~m2
         (persistent! ~m2)))))


(defn- assoc-transform-kvs [rewrite-fn kvs]
  (->> kvs
       (partition 2)
       (map
         (fn [[ks v]]
           [ks
            (fn [msym k]
              (rewrite-fn
                `(assoc! ~msym ~k ~v)))]))))


(defn- update-transform-kv [rewrite-fn ks f args]
  [[ks
    (fn [msym k]
      (rewrite-fn
        `(let [x# (get ~msym ~k nil)
               x# (~f x# ~@args)]
           (assoc! ~msym ~k x#))))]])


(defn- clear-field-transform-kv [rewrite-fn k]
  [[[k]
    (fn [msym k]
      (rewrite-fn
        `(clear-field! ~msym ~k)))]])

(defn- assoc-if-transform-kvs [rewrite-fn k v]
  [[[k]
    (fn [msym k]
      (let [v2 (gensym 'v)]
        (rewrite-fn
          `(let [~v2 ~v]
             (if (some? ~v2)
               (assoc! ~msym ~k ~v2)
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
                  (fn [msym _]
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
                   (keyword? x)            `(get ~g ~x ~(second subform))
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
                                       `(get ~form nil)
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



