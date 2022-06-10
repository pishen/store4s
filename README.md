# store4s

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/net.pishen/store4s_2.13/badge.svg)](https://maven-badges.herokuapp.com/maven-central/net.pishen/store4s_2.13)
[![javadoc](https://javadoc.io/badge2/net.pishen/store4s_2.13/javadoc.svg)](https://javadoc.io/doc/net.pishen/store4s_2.13)

A Scala library for [Google Cloud Datastore](https://cloud.google.com/datastore), providing compile-time mappings between case classes and Datastore entities, and a type-safe query DSL.

## Installation

For regular use:
```scala
libraryDependencies += "net.pishen" %% "store4s" % "<version>"
```

For [datastore-v1](https://github.com/googleapis/google-cloud-datastore) (compatible with Apache Beam):
```scala
libraryDependencies += "net.pishen" %% "store4s-v1" % "<version>"
```

## Encoding
Convert a case class to entity using `asEntity`:
```scala
import store4s._

case class Zombie(number: Int, name: String, girl: Boolean)

implicit val ds = Datastore.defaultInstance

// create an Entity without name/id
val z6 = Zombie(6, "Lily Hoshikawa", false).asEntity
// create an Entity with name
val z1 = Zombie(1, "Sakura Minamoto", true).asEntity("heroine")
// create an Entity with id
val z2 = Zombie(2, "Saki Nikaido", true).asEntity(2)
// create an Entity with case class property as name/id
val z3 = Zombie(3, "Ai Mizuno", true).asEntity(_.name)
```
The basic data types, `Seq`, `Option`, and nested case classes are supported.

### Custom types
To support custom types, one can create a `ValueEncoder` from an existing `ValueEncoder` using `contramap`:
```scala
val enc: ValueEncoder[LocalDate] =
  ValueEncoder.stringEncoder.contramap[LocalDate](_.toString)
```

### Interact with Datastore
To insert, upsert, or update the entity into datastore:
```scala
ds.add(z6)
ds.put(z1)
ds.update(z2)
```

### Exclude from indexes
To exclude properties from indexes, use the `excludeFromIndexes` function from `EntityEncoder`:
```scala
implicit val enc = EntityEncoder[Zombie].excludeFromIndexes(_.name, _.girl)

// z1 will have 'name' and 'girl' properties excluded
val z1 = Zombie(1, "Sakura Minamoto", true).asEntity("heroine")
```

## Decoding
Get an entity from datastore:
```scala
import store4s._
case class Zombie(number: Int, name: String, girl: Boolean)

val ds = Datastore.defaultInstance

val key1 = ds.keyFactory[Zombie].newKey("heroine")
val e1: Option[Entity] = ds.get(key1)
```

Decode an entity into case class using `decodeEntity`:
```scala
val zE: Either[Throwable, Zombie] = decodeEntity[Zombie](e1.get)
```

If you want to decode the entity directly and throw the Exception when it fail:
```scala
val zOpt: Option[Zombie] = ds.getRight[Zombie]("heroine")
```

To support custom types, one can create a `ValueDecoder` from an existing `ValueDecoder` using `map` or `emap`:
```scala
val dec: ValueDecoder[LocalDate] =
  ValueDecoder.stringDecoder.map(LocalDate.parse)
```

## Querying
A query can be built using the `Query` object:
```scala
import store4s._
case class Zombie(number: Int, name: String, girl: Boolean)

implicit val ds = Datastore.defaultInstance

val q = Query[Zombie]
  .filter(_.girl)
  .filter(_.number > 1)
  .sortBy(_.number.desc)
  .take(3)

val r1: EntityQuery = q.builder().build()
val r2: Seq[Entity] = q.run.getEntities
val r3: Seq[Either[Throwable, Zombie]] = q.run.getEithers
val r4: Seq[Zombie] = q.run.getRights
```

Use `getRights` to decode the Entities and throw Exceptions if any decoding failed.

For querying on [array type values](https://cloud.google.com/datastore/docs/concepts/queries#multiple_equality_filters), which corresponds to `Seq`, an `exists` function is available:
```scala
import store4s._
case class Task(tags: Seq[String])

implicit val ds = Datastore.defaultInstance

Query[Task]
  .filter(_.tags.exists(_ == "Scala"))
  .filter(_.tags.exists(_ == "rocks"))
  .run
```

For querying on the properties of embedded entity (which can be referred using `.`):
```scala
import store4s._
case class Hometown(country: String, city: String)
case class Zombie(name: String, hometown: Hometown)

implicit val ds = Datastore.defaultInstance

Query[Zombie]
  .filter(_.hometown.city == "Saga")
  .run
```

Check the [testing code](store4s/src/test/scala/store4s/QuerySpec.scala) for more supported features.

## ADT (Algebraic Data Types)

Support for encoding/decoding ADT is achieved by adding a property named `_type` into entities. When encoding a trait like this:

```scala
sealed trait Member
case class Zombie(name: String) extends Member
case class Human(name: String) extends Member

val member: Member = Human("Maimai Yuzuriha")

member.asEntity
```

The result entity will be:

```
key {
  path {
    kind: "Member"
  }
}
properties {
  key: "_type"
  value {
    string_value: "Human"
  }
}
properties {
  key: "name"
  value {
    string_value: "Maimai Yuzuriha"
  }
}
```

Which can then be decoded using

```scala
decodeEntity[Member]
```

The property name `_type` can be configured using `typeIdentifier` in `Datastore`:

```scala
implicit val ds = Datastore.defaultInstance.copy(typeIdentifier = "typeName")
```

## Transaction

Use `transaction` to create a Transaction:

```scala
implicit val ds = Datastore.defaultInstance

val zOpt = ds.transaction { tx =>
  val zOpt = tx.getRight[Zombie]("heroine")
  tx.add(z6)
  tx.put(z1)
  tx.update(z2)
  tx.delete(key1)
  zOpt
}
```

The Transaction will be committed once the function is completed, or rollbacked if an Exception is thrown.
