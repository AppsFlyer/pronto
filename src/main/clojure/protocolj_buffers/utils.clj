(ns pronto.utils)


(defn sanitized-class-name [^Class clazz]
  (clojure.string/replace (.getName clazz) "." "-"))

(defn class-name->wrapper-class-name [^Class clazz]
  (symbol (str 'wrapped- (sanitized-class-name clazz))))

(defn class-name->transient-class-name [^Class clazz]
  (symbol (str 'transient- (sanitized-class-name clazz))))

(defn transient-ctor-name [^Class clazz]
  (symbol (str '-> (class-name->transient-class-name clazz))))

(defn ->kebab-case [s]
  (clojure.string/lower-case (clojure.string/join "-" (clojure.string/split s #"_"))))

(defn with-type-hint [sym ^Class clazz]
  (with-meta sym {:tag (symbol (.getName clazz))}))


(defn ctor-name [^Class clazz]
  (symbol (str 'proto-> (clojure.string/replace (.getName clazz) "." "-"))))

