# pronto

[![Coverage Status](https://coveralls.io/repos/github/AppsFlyer/pronto/badge.svg?branch=master)](https://coveralls.io/github/AppsFlyer/pronto?branch=master)
[![Clojars Project](https://img.shields.io/clojars/v/com.appsflyer/pronto.svg)](https://clojars.org/com.appsflyer/pronto)
[![cljdoc badge](https://cljdoc.org/badge/com.appsflyer/pronto)](https://cljdoc.org/d/com.appsflyer/pronto/CURRENT)

A library for using [Protocol Buffers](https://github.com/protocolbuffers/protobuf) 3 in Clojure.

## Rationale

The guiding principles behind `pronto` are:

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

           [com.appsflyer/pronto "3.0.0"]

Note that the library comes with no Java protobuf dependencies of its own and they are expected to be provided by consumers of the library, with a minimal version of `3.15.0`.

## How does it work?

The main abstraction in `pronto` is the `proto-map`, a type of map which can be used as a regular Clojure map, but rejects any operations
which would break its schema. The library generates a bespoke `proto-map` class for every `protoc`-generated Java class (POJO).

Every `proto-map` 
* Holds an underlying instance of the actual POJO.
* Can be used as Clojure maps and support most Clojure semantics and abstractions by implementing all the appropriate internal Clojure interfaces 
(see [fine print](#fine-print-please-read)).
* Is immutable, i.e, `assoc`ing creates a new proto-map instance around a new POJO instance.

## Quick example

Let's use this [example](https://github.com/AppsFlyer/pronto/blob/master/resources/proto/people.proto):

```clj
(import 'protogen.generated.People$Person)

(require '[pronto.core :as p])

(p/defmapper my-mapper [People$Person])
```

`defmapper` is a macro which generates new `proto-map` classes for the supplied class and for any message type dependency it has. It also defines a var at the call-site, which serves as a handle to interact with the library later on.

Now we can work with protobuf while writing idiomatic Clojure code:

```clj
(-> (p/proto-map mapper People$Person) ;; create a Person proto-map
    (assoc :name "Rich" :id 0 :pet_names ["FOO" "BAR"])
    (update :pet_names #(map clojure.string/lower-case %))
    (assoc-in [:address :street] "Broadway"))
```

Internally, field reads and writes are delegated directly to the underlying POJO.
For example, `(:name person-map)` will call `Person.getName` and `(assoc person-map :name "John")` will call `Person.Builder.setName`.

Schema-breaking operations will fail:

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
are not always identical. Clojure maps are dynamic and open; Protocol-buffers are static and closed. This leads to
several design decisions, where we usually preferred to stick to Protocol-buffers' semantics rather than Clojure's.
This is done in order to remove ambiguity, and because we assume that a protocol-buffers user would like to ensure
the properties for which they decided to use them in the first place are maintained.

The main differences and the reasoning behind them are as follows:

* A `proto-map` contains the **entire set of keys** defined in a schema (as Clojure keywords) -- the schema is the source of truth and it is always present in its entirety.
* `dissoc` is unsupported -- for the reason above.
* Trying to `get` a key not in the map will throw an error, rather than return `nil`, for two reasons; First, `proto-maps` are closed
and can't be used as a general-purpose container of key-value pairs. Therefor, this is probably a mistake and we'd like to give the user immediate feedback.
Second, returning `nil` could lead to strange ambiguities -- see below.
* Associng a key not present in the schema is an error -- maintain schema correctness.
* Associng a value of the wrong type is an error -- maintain schema correctness.
* To `nil` or not to `nil`: protocol buffers in Java have no notion of scalar nullability. Scalar fields are always initialized and present.
When unset, they take on their "zero-value" rather than `null`. However, for message type fields it is possible to check whether set or not.
  * Scalar fields will never be `nil`. When unset, their value will be whatever the default value is for the respective type. However, protobuf provides
 [boxed](#well-known-types)  wrappers for primitive types, which `pronto` automatically recognizes and inlines into the proto-map.
  * The value message type fields will be `nil` when they are unset. Associng `nil` to a message type field will clear it.

## Usage guide

### Creating a new map:

```clj
(import 'protogen.generated.People$Person)

(require '[pronto.core :as p])

(p/defmapper my-mapper People$Person)

;; Create a new empty Person proto-map:
(p/proto-map my-mapper People$Person)

;; Serialize a byte array into a proto-map (and accompanying POJO):
(p/proto-map->bytes my-proto-map)

;; Deserialize byte array into a proto-map (and accompanying POJO):
(p/bytes->proto-map my-mapper People$Person (read-person-byte-array-from-kafka))

;; Generate a new proto-map from a Clojure map adhering to the schema:
(p/clj-map->proto-map my-mapper People$Person {:id 0 :name "hello" :address {:city "London"}})

;; Wrap around an existing instance of a POJO:
(let [person (. (People$Person/newBuilder) build)]
  (p/proto->proto-map my-mapper person))
  
;; Get the underlying POJO of a proto-map:
(p/proto-map->proto my-proto-map)
```

### Pro tip: On `proto-map`s scope
When creating data you can control when exactly you stop working with maps and start working with `proto-map`s. A `proto-map` has the advantage of failing fast. Hence `assoc`ing an invalid field (wrong type, non-existent enum etc.) generates failures at the crime scene. This is a _good_ thing since you want to locate the bug quickly. However, this comes with the cost of creating `proto-maps`.

```clj
(defn person-with-address [city]
  (let [addr (p/clj-map->proto-map my-mapper People$Address {:city city})]
    (p/clj-map->proto-map my-mapper People$Person {:id 0 :name "hello" :address addr})))
```

is mouthful. While it fails for every mistake _at the right place_ deeply nested structures creation quickly becomes bloated this way. 

However, this is also a valid code:

```clj
(defn person-with-address [city]
  (->> {:id 0 :name "hello" :address {:city city}}
       (p/clj-map->proto-map my-mapper People$Person))
```

It has the downside that you might have gotten either `Person` or `Address` wrong, but figuring which one is still easy enough. The point to move from plain maps into `proto-map`s can be chosen freely and should balance this tradeoff. 

### Protocol Buffers - Clojure interop

#### Fields

As discussed [previously](#fine-print-please-read), a `proto-map` contains the **entire set of keys** defined in a schema, represented by a keyword of their original
field name in the `.proto` file. 

However, you can control the naming strategy of keys. For example, if you'd like to use kebab-case:

```clj
(require '[pronto.utils :as u])
(p/defmapper my-mapper People$Person 
    :key-name-fn u/->kebab-case)
```

#### Scalar fields
Scalar fields are straight-forward in that that they follow the [protobuf Java scalar mappings](https://developers.google.com/protocol-buffers/docs/proto3#scalar).

Clojure-specific numeric types such as `Ratio` and `BigInt` are supported as well, and when `assoc`ing them to a map they are converted automatically
to the underlying field's type.

It is also important to note that Clojure uses `long`s to represent natural numbers, and these will be down-casted to `int` for integer fields.

In any case, handling of overflows is left to the user.

#### Message types
When calling `defmapper`, the macro will also find all message types on which the class depends, and generate specialized wrapper types for them as well,
so you do not have to call `defmapper` recursively yourselves.

When reading a field whose type is a message type, a `proto-map` is returned.

It is possible to assoc both a `proto-map` into a message type field, or a regular Clojure map -- as long as it adheres to the schema.

#### Repeated and maps
Values of repeated/map fields are returned as Clojure maps/vectors:

```clj
(:pet_names person-map)
=> ["foo" "bar"]
(:relations person-map)
=> {"friend" {:name "Joe" ... } "cousin" {:name "Vinny" ... }}
```

#### Enums
Enumerations are also represented by a keyword:

```clj
(import 'protogen.generated.People$Like)
(p/defmapper my-mapper People$Like)

(:level (p/proto-map my-mapper People$Like)) ;; either Level/LOW, Level/MEDIUM, Level/HIGH
=> :LOW
```
It is possible to use kebab-case (or any other case) for enums. 

```clj
(p/defmapper my-mapper People$Like
    :enum-value-fn u/->kebab-case)
(:level (p/proto-map my-mapper People$Like))
=> :low
```

Either a keyword or a Java enum value may be assoced:

```clj
(assoc (p/proto-map mapper People$Like) :level :HIGH)

(assoc (p/proto-map mapper People$Like) :level People$Level/HIGH)
```

#### One-of's
one-of's behave like other fields. This means that even when unset, the optional
fields still exist in the schema with their default values or `nil` in the case of message types.

To check which one-of is set, use `which-one-of` or `one-of`.

For example, given this schema:
```protobuf
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
This means that rather than calling `(-> my-proto-map :my-string-value :value)` you can simply write `(:my-string-value my-proto-map)`. Note that since
well-known-types are message types, this may return `nil` when the field is unset -- allowing us to model schemas which support null scalar fields.


#### Encoders

While protobuf allows us to describe our domain model, the Java generated types are not always a great programmatic fit. Consider the following schema:

```protobuf
message UUID {
   int64 msb = 1; // most significat bits
   int64 lsb = 2; // least significat bits
}

message Person {
   UUID id = 1;
}

```
Reading a person's `id` field would return a ```{:lsb <lsb> :msb <msb>}``` proto-map.

Encoders allow us to define an alternative type (rather than the POJO class) that will be used for proto-map fields of that type:
```clj
(defmapper mapper [protogen.generated.People$Person]
  :encoders {protogen.generated.People$UUID
             {:from-proto (fn [^protogen.generated.People$UUID proto-uuid]
                            (java.util.UUID. (.getMsb proto-uuid) (.getLsb proto-uuid)))                           
              :to-proto   (fn [^java.util.UUID java-uuid]
                            (let [b (People$UUID/newBuilder)]
                              (.setMsb b (.getMostSignificantBits java-uuid)
                              (.setLsb b (.getLeastSignificantBits java-uuid))
                              (.build b))))}})

(proto-map mapper People$Person :id (java.util.UUID/randomUUID))
=> {:id #uuid "2a1ef325-c7c2-42d4-815d-6bb1b9ed2e63"} 

```
This encourages DRYer code, since these kinds of proto<->clj conversions can be defined as a single encoder, rather than handled across the codebase.

#### Interoping proto-maps with Java code

It is sometimes necessary to interop with Java code that expects a POJO instance. For example, consider the following method signature:


```java
public class Utils {
  public static void foo(com.google.protobuf.Duration duration) { ... }   
}  
```

This method receives a `com.google.protobuf.Duration`, a generated class that was compiled from the [duration schema](https://github.com/protocolbuffers/protobuf/blob/master/src/google/protobuf/duration.proto) that is part of the protobuf distribution. 

Since proto-maps are thin wrappers, we can always refer back to the underlying POJO and interop successfully:

```clj
(require '[pronto.core :as p])
(import 'com.google.protobuf.Duration)

(p/defmapper m [Duration])

(Utils/foo (p/proto-map->proto (p/proto-map m Duration)))
```

If your Java code operates on the protoc generated interfaces rather than concrete typs, it is also possible to pass the proto-map directly:

```java
public static void foo(com.google.protobuf.DurationOrBuilder duration) { ... }
```

```clj
(Utils/foo (p/proto-map m Duration))
```

## [Performance](doc/performance.md)

Please read the [performance introduction](doc/performance.md).

## Schema utils

To inspect a schema at the REPL use `pronto.schema/schema`, which returns the (Clojurified) schema as data:

```clj
(require '[pronto.schema :refer [schema]])

(schema People$Person)
=> {:diet #{"UNKNOWN_DIET" "OMNIVORE" "VEGETARIAN" "VEGAN"} ;; an enum
    :address People$Address ;; address field
    :address_book {String People$PersonDetails} ;; a map string->PersonDetails
    :age  int
    :friends [People$Person] ;; a repeated Person fields
    :name String}
```
Drilling-down is also possible:
```clj
(p/schema People$Person :address)
=> {:country String :city String :house_num int}
```

Please note that unlike the rest of the library, `schema` uses runtime reflection and is meant as a convenience method to be used during development. 

