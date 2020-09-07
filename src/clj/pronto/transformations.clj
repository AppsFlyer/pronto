(ns pronto.transformations)

(defn map->proto-map [empty-proto-map m]
  (let [m (or m {})]
    (if (map? m)
      (with-meta
        (persistent!
          (reduce (fn [acc [k v]]
                    (assoc! acc k v))
                  empty-proto-map
                  m))
        (meta m))
      (throw (IllegalArgumentException. (str "cannot wrap " (class m)))))))

