(ns pronto.lens
  (:require [pronto.utils :as u]
            [pronto.type-gen :as t]
            [pronto.emitters :as e]
            [clojure.walk :as walk])
  (:import [pronto ProtoMap ProtoMapper]
           [com.google.protobuf GeneratedMessageV3$Builder]))


(def ^:private ^:dynamic *hints*)


#_{:clj-kondo/ignore [:unused-binding]}
(defmacro hint [proto-map clazz mapper]
  (throw (new IllegalStateException "hint can only be used inside p-> or with-hints")))


(defn try-hint [form clazz mapper]
  (if (and (u/safe-resolve mapper)
           (class? clazz))
    `(hint ~form ~(symbol (.getName ^Class clazz)) ~mapper)
    form))


(defn- resolvable-mapper? [m]
  (when-let [m (u/safe-resolve m)]
    (and (var? m) (u/mapper? @m))))

(defn- unpack-hint [x]
  (let [hint? (and (coll? x)
                   (= (count x) 4)
                   (= "hint" (name (first x))))]
    (when hint?
      (let [[_ proto-map-sym clazz mapper] x
            clazz         (u/safe-resolve clazz)]
        (when-not (class? clazz)
          (throw (ex-info "not a class" {:class clazz})))
        (when-not (resolvable-mapper? mapper)
          (throw (ex-info "not resolvable mapper" {:mapper mapper})))
        {:sym proto-map-sym :hint {:mapper mapper :type-hint clazz}}))))


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


(defn- find-field [clazz k ctx]
  (when clazz
    (first
      (filter #(= k (:kw %))
              (t/get-field-handles clazz ctx)))))


(defn- do-get
  ([ctx clazz k msym] (do-get ctx clazz k msym nil))
  ([ctx clazz k msym default-val]
   (if (and clazz (keyword? k))
     (if-let [field (find-field clazz k ctx)]
       `(if ~msym
          (or ~(e/getter clazz field msym) ~default-val)
          ~default-val)
       (throw (ex-info "unknown field for class" {:class clazz :field k})))
     `(get ~msym ~k ~default-val))))


(defn- do-assoc [ctx clazz m builder k v]
  (if (and clazz (keyword? k))
    (if-let [field (find-field clazz k ctx)]
      (e/setter clazz field builder v false)
      (throw (ex-info "unknown field for class" {:class clazz :field k})))
    `(assoc! ~m ~k ~v)))


(defn- field-type-hint [type-hint k ctx]
  (when type-hint
    (when-let [fd (:fd (find-field type-hint k ctx))]
      (when (u/struct? fd)
        (t/field-type type-hint fd)))))

(defn- transform-in [m type-hint ctx kvs]
  (let [m             (u/with-type-hint m ProtoMap)
        m2            (u/with-type-hint (gensym 'm2) ProtoMap)
        builder       (gensym 'builder)
        new-submap    (gensym 'newsubmap)
        submap        (gensym 'submap)
        pmap?         (gensym 'pmap?)
        kv-forest     (u/kv-forest kvs)
        ctx           (when ctx (assoc ctx :pronto/fqn? true))
        use-type-hint? (and type-hint
                            (->> kv-forest
                                 (map first)
                                 (every? keyword?)))]
    `(let [~pmap?               (u/proto-map? ~m)
           ~m2                  ~(if use-type-hint?
                                   m
                                   `(if (and ~pmap? (.isMutable ~m)) ~m (transient ~m)))
           ~builder             (when ~pmap? (.pmap_getBuilder ~m2))]
       ~@(doall
          (for [[k vs] kv-forest
                v      (partition-by u/leaf? vs)]
            (if (u/leaf? (first v))
              `(do
                 ~@(doall
                    (for [leaf v]
                      (let [val-fn  (u/leaf-val leaf)]
                        (val-fn ctx (when use-type-hint? type-hint) m2 builder k)))))
              (let [new-type-hint (field-type-hint type-hint k ctx)]
                `(let [~submap     (or ~(do-get ctx
                                                (when use-type-hint? type-hint)
                                                k
                                                (if use-type-hint? builder m2))
                                       (when ~pmap?
                                         (.empty ~m2 ~k)))
                       ~new-submap ~(transform-in submap new-type-hint ctx (u/flatten-forest v))]
                   ~(do-assoc ctx (when use-type-hint? type-hint) m2 builder k new-submap))))))
       ~(if use-type-hint?
          `(.fromBuilder ~m2 ~(u/with-type-hint builder GeneratedMessageV3$Builder))
          `(persistent! ~m2)))))


(defn- assoc-transform-kvs [rewrite-fn kvs]
  (->> kvs
       (partition 2)
       (map
         (fn [[ks v]]
           [ks
            (fn [ctx clazz msym bsym k]
              (rewrite-fn
                (do-assoc ctx clazz msym bsym k v)))]))))


(defn- update-transform-kv [rewrite-fn ks f args]
  [[ks
    (fn [ctx clazz msym bsym k]
      (rewrite-fn
       (let [v (gensym 'v)]
         ;; TODO: this do-get is a bit hacky in the sense it implicitly assumes
         ;; it should use bsym if clazz is present. Maybe encode this more explicitly
         ;; or unify msym and bsym into a single variable across this interface.
         `(let [x# ~(do-get ctx clazz k (if clazz bsym msym))
                ~v (~f x# ~@args)]
            ~(do-assoc ctx clazz msym bsym k v)))))]])


(defn- clear-field-transform-kv [rewrite-fn k]
  [[[k]
    (fn [_ctx _clazz msym _bsym k]
      (rewrite-fn
        `(clear-field! ~msym ~k)))]])

(defn- assoc-if-transform-kvs [rewrite-fn k v]
  [[[k]
    (fn [ctx clazz msym bsym k]
      (let [v2 (gensym 'v)]
        (rewrite-fn
          `(let [~v2 ~v]
             (if (some? ~v2)
               ~(do-assoc ctx clazz msym bsym k v2)
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


(defn- transformation? [form]
  (or
    (assoc? form)
    (update? form)
    (clear? form)))


(defn- rewrite-transformation [g type-hint ctx transforms]
  {:next-type-hint type-hint
   :code           (transform-in
                    g
                    type-hint
                    ctx
                    (doall
                     (mapcat
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
                            #_(let [[f & args] form]
                              [[[(keyword (gensym))]
                                (fn [_ _ msym _ _]
                                  (rewrite-fn
                                   (if args
                                     `(~f ~msym ~@args)
                                     `(~f ~msym))))]]))))
                      transforms)))})



(declare rewrite-forms)

(defn- rewrite-get [sym subform mapper ctx type-hint]
  (let [x         (first subform)
        predicate (-> x meta :predicate)
        rewritten
        (cond
          (keyword? x)            {:code           (do-get ctx type-hint x sym (second subform))
                                   :next-type-hint
                                   (field-type-hint type-hint x ctx)}
          (= 'get (fn-name x))    {:code (do-get ctx type-hint (second x) sym
                                                 (when (= 3 (count x))
                                                   (nth x 2)))
                                   :next-type-hint
                                   (field-type-hint type-hint (second x) ctx)}
          (= 'get-in (fn-name x)) (let [{:keys [code next-type-hint]} (rewrite-forms
                                                                       sym
                                                                       type-hint
                                                                       mapper
                                                                       (second x))]
                                    {:code
                                     `(as-> ~sym ~sym ~@code)
                                     :next-type-hint next-type-hint})
          :else                     {:code `(-> ~sym ~x)})]
    (update rewritten
            :code
            (fn [code]
              (if (nil? predicate)
                code
                `(if ~predicate ~code ~sym))))))


(defn- rewrite-forms [g type-hint mapper forms]
  (let [mapper (if (var? mapper)
                 mapper
                 (u/safe-resolve mapper))
        ctx    (when mapper
                 (assoc (.getContext ^ProtoMapper @mapper)
                        :pronto/fqn? true
                        :instrument? false))]
    (loop [forms (partition-by
                  (fn [form]
                    (if (transformation? form)
                      ::transformation
                      form))
                  forms)
           type-hint type-hint
           res []]
      (let [subform (first forms)]
        (if-not subform
          {:code res :next-type-hint type-hint}
          (let [x         (first subform)
                {:keys [code next-type-hint]} (if (and (coll? subform)
                                                       (transformation? x))
                                                (rewrite-transformation g type-hint ctx subform)
                                                (rewrite-get g subform mapper ctx type-hint))]
            (recur
             (rest forms)
             next-type-hint
             (conj res code))))))))


(defn- find-hint [binding]
  (let [local-hint (unpack-hint binding)
        scope-hint (get *hints* binding)]
    (or local-hint scope-hint)))


(defmacro p->
  "Like `->` but meant to be used where the initial expression evaluates to a proto-map. Under the hood, `p->` will operate on a transient version of the proto-map and `persistent!` it back when done. Expressions will be pipelined as much as possible (but never reordered) such that `(p-> person-proto (assoc-in [:pet :name] \"patch\") (assoc-in [:pet :kind] :cat))` will only generate a single instance of a pet transient map to which both operations will be applied in succession."
  [x & forms]
  (let [{:keys [sym hint]}         (find-hint x)
        {:keys [type-hint mapper]} hint
        g                          (gensym)]
    `(clojure.core/as-> ~(or sym x) ~g
       ~@(:code (rewrite-forms g type-hint mapper forms)))))


(defmacro pcond->
  "Equivalent to cond->. See `p->`"
  [expr & clauses]
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


(defmacro with-hints [hints & body]
  (let [hints (->> hints
                   (keep unpack-hint)
                   (map (juxt :sym identity))
                   (into {}))]
    (binding [*hints* hints]
      (let [body (walk/macroexpand-all body)]
        `(do ~@body)))))
