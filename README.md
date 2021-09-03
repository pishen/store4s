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

implicit val datastore = Datastore.defaultInstance

val z6 = Zombie(6, "Lily Hoshikawa", false).asEntity

// provide a name
val z1 = Zombie(1, "Sakura Minamoto", true).asEntity("heroine")
// provide an id
val z2 = Zombie(2, "Saki Nikaido", true).asEntity(2)
```
The basic data types, `Seq`, `Option`, and nested case classes are supported.

To insert, upsert, or update the entity into datastore:
```scala
datastore.add(z6)
datastore.put(z1)
datastore.update(z2)
```

## Decoding
Get an entity from datastore:
```scala
import store4s._
case class Zombie(number: Int, name: String, girl: Boolean)

val datastore = Datastore.defaultInstance

val key1 = datastore.keyFactory[Zombie].newKey("heroine")
val e1: Option[Entity] = datastore.get(key1)
```

Decode an entity into case class using `decodeEntity`:
```scala
val z: Either[Throwable, Zombie] = decodeEntity[Zombie](e1.get)
```

## Querying
A query can be built using the `Query` object:
```scala
import store4s._
case class Zombie(number: Int, name: String, girl: Boolean)

implicit val datastore = Datastore.defaultInstance

Query[Zombie]
  .filter(_.girl)
  .filter(_.number > 1)
  .sortBy(_.number.desc)
  .take(3)
  .run
```

Check the [testing code](store4s/src/test/scala/store4s/QuerySpec.scala) for more supported features.
