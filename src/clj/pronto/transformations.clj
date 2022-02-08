(ns pronto.transformations)

(defn ^:no-doc map->proto-map [empty-proto-map m]
  (with-meta
    (persistent!
     (reduce-kv
      (fn [acc k v]
        (assoc! acc k v))
      empty-proto-map
      m))
    (meta m)))

