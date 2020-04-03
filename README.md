# pronto

A library for using [Protocol Buffers](https://github.com/protocolbuffers/protobuf) 3 in Clojure.

## Rationale

The guiding principles for `pronto` are:

* **Idiomatic interaction**: Use Protocol Buffer POJOs (`protoc` generated) as though they were native Clojure data structures, allowing for data-driven programming.
* **Minimalistic**: `pronto` is behavioral only: it is only considered with making POJOs mimic Clojure collections. Data is still stored in the POJOs, and no
kind of reflective/dynamic APIs are used. This also has the benefit that [unknown fields](https://developers.google.com/protocol-buffers/docs/proto3#unknowns) are not lost 
during serialization.
* **Runtime Type Safety**: The schema cannot be broken - `pronto` fails-fast when `assoc`ing a key not present in the schema or a value of the wrong type.
This guarantees that schema errors are detected immediately rather than at some undefined time in the future (perhaps too late) or worse -- dropped and
ignored completely.
* **Performant**: Present a minimal CPU/memory overhead: `pronto` compiles very thin wrapper classes and avoids reflection completely.

## Installation
Add a dependency to your `project.clj` file:

           [bla 1.0.0]

## How does it work?

Let's take a look by using this [example](https://***REMOVED***/Architecture/pronto/blob/deftype/resources/proto/people.proto): 

```clj
(import 'protogen.generated.People$Person)

(require '[pronto.core :as p])

(p/defproto People$Person)
=> user.People$PersonMap
```
`defproto` is a macro which accepts a `protoc`-generated class, and generates new bespoke wrapper classes for it and for any message dependency it has.
In this example, we generated a new class `user.People$PersonMap` in the namespace in which the macro was expanded (as well as classes for its dependencies, 
for example `user.People$Address`).

Instances of the wrapper class:

* Hold an underlying instance of the actual Java object.
* Can be used as Clojure maps and support Clojure semantics and abstractions by implementing all the appropriate internal Clojure interfaces.
* Are immutable.

Now we can work with protobuf while writing idiomatic Clojure code:

```clj
(def person (. (People$Person/newBuilder) build))

(def person-map (proto->People$PersonMap person))

(-> person-map
    (assoc :name "Rich" :id 0 :pet-names ["FOO" "BAR"])
    (update :pet-names #(map clojure.string/lower-case %))
    (assoc-in [:address :street] "Broadway"))

```

Internally, field reads and writes are delegated directly to the underlying Java instance.
For example, `(:name person-map)` will call `Person.getName` and `(assoc person-map :name "John")` will call `Person.Builder.setName`.

Schema-breaking operations will throw an error:

```clj
(assoc person-map :no-such-key 12345)
=> Execution error (IllegalArgumentException) at user.People$PersonMap/assoc
No such field :no-such-key

(assoc person-map :name 12345)
=> Execution error (IllegalArgumentException) at user.People$PersonMap/assoc
expected class java.lang.String, but got class java.lang.Long
```

## Usage guide

### Creating a new map:
Calling `defproto` also generates specialized constructor functions for instantiating the wrapper class:

```clj
(def person (. (People$Person/newBuilder) build))

;; wrap around an instance of the class
(proto->People$PersonMap person)

;; generate a new instance of `People$Person` from a Clojure map adhering to the schema, and wrap around it:
(map->People$PersonMap {:id 0 :name "hello" :address {:city "London"}}) 

;; deserialize byte array into People$Person and wrap around it:
(bytes->People$PersonMap (.toByteArray person))

;; empty Person:
(->People$PersonMap)

```

As well as their reverse:

```clj
(People$PersonMap->proto person-map) ;; obtain the Java instance.
(People$PersonMap->map person-map) ;; obtain a regular Clojure map
(People$PersonMap->bytes person-map) ;; serialize to byte array
```

### Protocol Buffers - Clojure interop

#### Fields

A proto map contains the **entire set of keys** defined in a schema, represented by a keyword of their Clojurified name.
If a key has never been set, its default protobuf value will be returned.

```clj
(->People$PersonMap)
=> 
{:id 0,
 :name "",
 :email "",
 :address
 {:city "",
  :street "",
  :house-num 0,
  :house {:num-rooms 0},
  :apartment {:floor-num 0}},
 ...}
```


In order to prevent ambiguity and stay aligned with protobuf semantics, `assoc`ing `nil` values is not allowed for any type of key,
and will throw an `IllegalArgumentException`.

Since keys cannot be removed from the map, `dissoc` is also unsupported.

To explicitly clear a value, use `clear-field`:

```clj
;; will internally call People$Person.Builder.clearName
(p/clear-field (map->People$PersonMap {:name "Joe"}) :name)
=> {:name "", ... }
```

To check if a field has been set, use `has-field?` (only supported for message types, by protobuf 3 design):

```clj
;; will internally call People$Person.hasAddress
(p/has-field? (->People$PersonMap) :address)
=> false
(p/has-field? (map->People$PersonMap {:address {:city "NYC"}}) :address)
=> true
```

#### Scalar fields
Scalar fields are straight-forward in that that they follow the [protobuf Java scalar mappings](https://developers.google.com/protocol-buffers/docs/proto3#scalar).

Clojure-specific numeric types such as `Ratio` and `BigInt` are supported as well, and when `assoc`ing them to a map they are converted automatically
to the underlying field's type.

It is also important to note that Clojure uses `long`s to represent natural numbers, and these will be down-casted to `int` for integer fields.

In any case, handling of overflows is left to the user.

#### Message types
When calling `defproto`, the macro will also find all message types on which the class depends, and generate specialized wrapper types for them as well.

When reading a field whose type is a message type, a wrapper instance is returned:
```clj
(type (:address (->People$PersonMap)))
=> user.People$AddressMap
```

#### Repeated and maps
Values of repeated/map fields are returned as Clojure maps/vectors:

```clj
(:pet-names person-map)
=> ["foo" "bar"]
(:relations person-map)
=> {:friend "Joe" :cousin "Vinny"}
```

#### Enums
Enumerations are also represented by a keyword of their Clojurified name:

```clj
(:level (->People$LikeMap)) ;; either Level/LOW, Level/MEDIUM, Level/HIGH
=> :low
```

#### One-of's
one-of's behave like other fields. This means that even when unset, the optional
fields still exist in the schema with their default values.

To check which one-of is set, use `which-one-of`.

For example, given this schema:
```
message Address {
  string city = 1;
  string street = 2;
  int32 house_num = 3;
  oneof home {
    House house = 4;
    Apartment apartment = 5;
  }
}
```

```clj
(p/which-one-of (->People$AddressMap) :home)
=> :home-not-set

(p/which-one-of (map->People$AddressMap {:house {:num-rooms 3}}) :home)
=> :house
```

#### ByteStrings

`ByteString`s are not wrapped, and returned raw in order to provide direct access to the byte array.

However, ByteString's are naturally `seqable` since they implement `java.lang.Iterable`.

#### Transients

Proto maps are immutable. Modification is done via transitioning the underlying POJO instance
to its builder, setting the new value, and returning a new proto map backed up by the new instance returned from the builder.

Proto maps can be made `transient` by calling [transient](https://clojuredocs.org/clojure.core/transient) and then persistent again via [persistent!](https://clojuredocs.org/clojure.core/persistent!), and like regular transients, transient proto maps are immutable and not thread-safe and are intended to only be used 
in local scopes, to perform a series of update operations.

Rather then referencing the POJO instance, transients use a `Builder` instance. This eliminates the need to transition to the builder on every update operation, and can lower GC pressure.
