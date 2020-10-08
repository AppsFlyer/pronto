# pronto

A library for using [Protocol Buffers](https://github.com/protocolbuffers/protobuf) 3 in Clojure.

***
This library is an `alpha` version and under active development!

**Please join #rnd-pronto-lib on Slack for discussions and announcements.**
****

## Rationale

The guiding principles for `pronto` are:

* **Idiomatic interaction**: Use Protocol Buffer POJOs (`protoc` generated) as though they were native Clojure data structures, allowing for data-driven programming.
* **Minimalistic**: `pronto` is behavioral only: it is only concerned with making POJOs mimic Clojure collections. Data is still stored in the POJOs, and no
kind of reflective/dynamic APIs are used. This also has the benefit that [unknown fields](https://developers.google.com/protocol-buffers/docs/proto3#unknowns) are not lost 
during serialization.
* **Runtime Type Safety**: The schema cannot be broken - `pronto` fails-fast when `assoc`ing a key not present in the schema or a value of the wrong type.
This guarantees that schema errors are detected immediately rather than at some undefined time in the future (perhaps too late) or worse -- dropped and
ignored completely.
* **Performant**: Present a minimal CPU/memory overhead: `pronto` compiles very thin wrapper classes and avoids reflection completely.

## Installation
Add a dependency to your `project.clj` file:

           [pronto "1.0.0"]

## How does it work?

The main abstraction of the library is the `proto-map`, a type of map which can be used as a regular Clojure map, but rejects any operations
which would break your schema. The library generates a bespoke `proto-map` class for every `protoc`-generated Java class (POJO).

Every `proto-map` 
* Holds an underlying instance of the actual POJO.
* Can be used as Clojure maps and support most Clojure semantics and abstractions by implementing all the appropriate internal Clojure interfaces 
(see [fine print](#fine-print-please-read)).
* Is immutable, i.e, `assoc`ing creates a new proto-map instance around a new POJO instance.

## Quick example

Let's use this [example](https://***REMOVED***/Architecture/pronto/blob/deftype/resources/proto/people.proto): 

```clj
(import 'protogen.generated.People$Person)

(require '[pronto.core :as p])

(p/defproto People$Person)
```

`defproto` is a macro which generates new `proto-map` classes for the supplied class and for any message type dependency it has.

Now we can work with protobuf while writing idiomatic Clojure code:

```clj
(-> (p/proto-map People$Person) ;; create a Person proto-map
    (assoc :name "Rich" :id 0 :pet_names ["FOO" "BAR"])
    (update :pet_names #(map clojure.string/lower-case %))
    (assoc-in [:address :street] "Broadway"))
```

Internally, field reads and writes are delegated directly to the underlying POJO.
For example, `(:name person-map)` will call `Person.getName` and `(assoc person-map :name "John")` will call `Person.Builder.setName`.

Schema-breaking operations will throw an error:

```clj
(assoc person-map :no-such-key 12345)
=> Execution error (IllegalArgumentException) at user.People$PersonMap/assoc
No such field :no-such-key

(assoc person-map :name 12345)
=> Execution error:  {:error :invalid-type,
                      :class protogen.generated.People$Person,
                      :field "name",
                      :expected-type java.lang.String,
                      :actual-type java.lang.Long,
                      :value 12345}
```

## Fine print - please read

It is important to realize that while `proto-maps`s look and feel like Clojure maps for the most part, their semantics
are not always identical. Clojure maps are dynamic and open-ended; Protocol-buffers are static and closed. This leads to
several design decisions, in which we usually prefer to stick to Protocol-buffers' semantics rather than Clojure's.
This is done in order to remove ambiguity, and because we assume that a user which uses protocol-buffers would like to ensure
the properties for which they decided to use it in the first place are kept.

The main differences and the reasoning behind them are listed below:

* A `proto-map` contains the **entire set of keys** defined in a schema (turned into Clojure keywords) -- the schema is the source of truth and it is always present in its entirety.
* `dissoc` is unsupported -- for the reason above.
* Trying to `get` a key not in the map will throw an error, rather than return `nil`. There are two reasons behind this. First, `proto-maps` are closed
and can't be used as a general-purpose container of key-value's. As a result, this is probably a bug and we'd like to give the user immediate feedback.
Secondly, returning `nil` could lead to strange ambiguities -- see below.
* Associng a key not present in the schema is an error -- maintain schema correctness.
* Associng a value of the wrong type is an error -- maintain schema correctness.
* To `nil` or not to `nil`: protocol buffers in Java have no notion of nullability. Every field in every message is always initialized and present.
When unset, they take on their "zero-value" rather than `null`. However, for message type fields it is possible to check whether set or not.
  * Scalar fields will never be `nil`. When unset, their value will be whatever the default value is for the respective type.
  * The value message type fields will be `nil` when they are unset. Associng `nil` to a message type field will clear it.

## Usage guide

### Creating a new map:

```clj
(import 'protogen.generated.People$Person)

(require '[pronto.core :as p])

(p/defproto People$Person)

;; Create a new empty Person proto-map:
(p/proto-map People$Person)

;; Serialize a byte array into a proto-map (and accompanying POJO):
(p/proto-map->bytes my-proto-map)

;; Deserialize byte array into a proto-map (and accompanying POJO):
(p/bytes->proto-map People$Person (read-person-byte-array-from-kafka))

;; Generate a new proto-map from a Clojure map adhering to the schema:
(p/clj-map->proto-map People$Person {:id 0 :name "hello" :address {:city "London"}})

;; Wrap around an existing instance of a POJO:
(let [person (. (People$Person/newBuilder) build)]
  (p/proto->proto-map person))
  
;; Get the underlying POJO of a proto-map:
(p/proto-map->proto my-proto-map)
```

***
Please note that `proto-map`, `bytes->proto-map` and `clj-map->proto-map` are -- for efficiency reasons --  macros and not functions.

This means that every call-site for the above must supply a symbol which will resolve to a Class during macro expansion time. Passing a variable
which references a Class instance will not work. In short, this basically means you cannot do the following:

```clj
(let [clazz People$Person]
   (p/proto-map clazz))
```
***

### Protocol Buffers - Clojure interop

#### Fields

As discussed [previously](#fine-print-please-read), a `proto-map` contains the **entire set of keys** defined in a schema, represented by a keyword of their original
field name in the `.proto` file. 

However, you can control the naming strategy of keys. For example, if you'd like to use kebab-case:

```clj
(require '[pronto.utils :as u])
(p/defproto People$Person 
    :key-name-fn u/->kebab-case)
```

#### Scalar fields
Scalar fields are straight-forward in that that they follow the [protobuf Java scalar mappings](https://developers.google.com/protocol-buffers/docs/proto3#scalar).

Clojure-specific numeric types such as `Ratio` and `BigInt` are supported as well, and when `assoc`ing them to a map they are converted automatically
to the underlying field's type.

It is also important to note that Clojure uses `long`s to represent natural numbers, and these will be down-casted to `int` for integer fields.

In any case, handling of overflows is left to the user.

#### Message types
When calling `defproto`, the macro will also find all message types on which the class depends, and generate specialized wrapper types for them as well,
so you do not have to call `defproto` recursively yourselves.

When reading a field whose type is a message type, a `proto-map` is returned.

It is possible to assoc both a `proto-map` into a message type field, or a regular Clojure map -- as long as it adheres to the schema.

#### Repeated and maps
Values of repeated/map fields are returned as Clojure maps/vectors:

```clj
(:pet_names person-map)
=> ["foo" "bar"]
(:relations person-map)
=> {:friend {:name "Joe" ... } :cousin {:name "Vinny" ... }}
```

#### Enums
Enumerations are also represented by a keyword:

```clj
(import 'protogen.generated.People$Like)
(p/defproto People$Like)

(:level (p/proto-map People$Like)) ;; either Level/LOW, Level/MEDIUM, Level/HIGH
=> :LOW
```
It is possible to use kebab-case (or any other case) for enums. 

```clj
(p/defproto People$Like
    :enum-value-fn u/->kebab-case)
(:level (p/proto-map People$Like))
=> :low
```

#### One-of's
one-of's behave like other fields. This means that even when unset, the optional
fields still exist in the schema with their default values or `nil` in the case of message types.

To check which one-of is set, use `which-one-of` or `one-of`.

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
(p/which-one-of (p/proto-map People$Address) :home)
=> nil

(p/one-of (p/proto-map People$Address) :home)
=> nil

(p/which-one-of (p/clj-map->proto-map People$Address {:house {:num_rooms 3}}) :home)
=> :house

(p/one-of (p/clj-map->proto-map People$Address {:house {:num_rooms 3}}) :home)
=> {:num_rooms 3}
```

#### ByteStrings
`ByteString`s are not wrapped, and returned raw in order to provide direct access to the byte array.

However, ByteString's are naturally `seqable` since they implement `java.lang.Iterable`.

#### Well-Known-Types

[Well known types](https://github.com/protocolbuffers/protobuf/blob/master/src/google/protobuf/wrappers.proto) fields will be inlined into the message.
This means, that rather than calling `(-> my-proto-map :my-string-value :value)` you can simply write `(:my-string-value my-proto-map)`. Note that since
well-known-types are message types, this may return `nil` when the field is unset.

### [Performance](doc/performance.md)

#### Reloadability 

TODO: discuss reloadability at the REPL


