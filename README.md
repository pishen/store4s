# store4s

A Scala library for [Google Cloud Datastore](https://cloud.google.com/datastore), using Google's [Java client](https://github.com/googleapis/java-datastore) underneath. This library provides a compile-time mapping between case classes and Datastore entities using [Magnolia](https://github.com/propensive/magnolia), and a type-safe query DSL implemented by Scala Macros.

## Installation
```scala
libraryDependencies += "net.pishen" %% "store4s" % "0.1.0"
```

## Encoding
Convert a case class to entity using `asEntity`:
```scala
import store4s._

case class Zombie(number: Int, name: String, girl: Boolean)

implicit val datastore = Datastore.defaultInstance

val z1 = Zombie(1, "Sakura Minamoto", true).asEntity("heroine")
val z2: Entity = Zombie(2, "Saki Nikaido", true).asEntity(2)
val z6 = Zombie(6, "Lily Hoshikawa", false).asEntity
```
Currently supported data types are `Blob`, `Boolean`, `Double`, `Key`, `LatLng`, `Seq`, `Option`, `Int` (cast to `Long`), `Long`, `String`, `Timestamp`, and nested case classes.

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

Parse an entity into case class using `decodeEntity`:
```scala
EntityDecoder[Zombie].decodeEntity(e1.get)
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

## Comparison
```scala
case class Task(category: String, done: Boolean, priority: Int, description: String)

//google-cloud-java
FullEntity.newBuilder(keyFactory.setKind("Task").newKey())
  .set("category", "Personal")
  .set("done", false)
  .set("priority", 4)
  .set("description", "Learn Cloud Datastore")
  .build()

//store4s
Task("Personal", false, 4, "Learn Cloud Datastore").asEntity

//google-cloud-java
Task(
  entity.getString("category"),
  entity.getBoolean("done"),
  entity.getLong("priority").toInt,
  entity.getString("description")
)

//store4s
EntityDecoder[Task].decodeEntity(entity)

//google-cloud-java
Query.newEntityQueryBuilder()
  .setKind("Task")
  .setFilter(
    CompositeFilter.and(
      PropertyFilter.eq("done", false),
      PropertyFilter.ge("priority", 4)
    )
  )
  .setOrderBy(OrderBy.desc("priority"))
  .build()

//store4s
Query[Task]
  .filter(!_.done)
  .filter(_.priority >= 4)
  .sortBy(_.priority.desc)
```
