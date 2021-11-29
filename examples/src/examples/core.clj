(ns examples.core
  (:import (protogen Person))
  (:require [pronto.core :as p]
            [pronto.schema :as sch]))

(comment
  ;; create a mapper:
  (p/defmapper m [Person])

  ;; create some people...
  (def people [(p/proto-map m Person
                            :id 0
                            :name "Jerry"
                            :age 32
                            :address {:city "NYC" :country "USA"})
               (p/proto-map m Person
                            :id 1
                            :name "George"
                            :age 31
                            :address {:city "NYC" :country "USA"})
               (p/proto-map m Person
                            :id 3
                            :name "Elaine"
                            :age 30
                            :address {:city "NYC" :country "USA"})
               (p/proto-map m Person
                            :id 4
                            :name "Kramer"
                            :age 35
                            :address {:city "NYC" :country "USA"})
               (p/proto-map m Person
                            :id 5
                            :name "Newman"
                            :age 38)])


  (def average-age (/ (transduce (map :age) + people)
                      (count people)))


  (->> people
       (filter
        (fn [{:keys [age]}]
          (zero? (mod age 2)))))


  (let [jerry (first people)]
    (p/p-> jerry
           (update :age inc)
           (assoc-in [:address :street] "Broadway")
           (assoc-in [:address :house_number] 158)
           (assoc :friends (->> people
                                (filter
                                  (comp
                                    (complement #{"Jerry" "Newman"})
                                    :name))))))


  (->> people
       ;; serialize
       (map p/proto-map->bytes)
       ;; deserialize. We must be explicitly state the type we wish to deserialize, itcannot be inferred
       (map #(p/bytes->proto-map m Person %)))


  (sch/schema Person)


  (sch/schema Person :address)

  ;; ======================================================================== ;;

  )
