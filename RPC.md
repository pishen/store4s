# store4s

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/net.pishen/store4s-rpc_2.13/badge.svg)](https://maven-badges.herokuapp.com/maven-central/net.pishen/store4s-rpc_2.13)
[![javadoc](https://javadoc.io/badge2/net.pishen/store4s-rpc_2.13/javadoc.svg)](https://javadoc.io/doc/net.pishen/store4s-rpc_2.13)

A Scala library for [Firestore in Datastore mode](https://cloud.google.com/datastore), providing compile-time mappings between case classes and Datastore entities, a type-safe query DSL, and asynchronous interfaces.

### Create an Entity
```scala
// Google's Java library
val taskKey = datastore.newKeyFactory().setKind("Task").newKey("sampleTask")
val task = Entity.newBuilder(taskKey)
  .set("category", "Personal")
  .set("done", false)
  .set("priority", 4)
  .set("description", "Learn Cloud Datastore")
  .build()

// store4s
val task = Task("Personal", false, 4, "Learn Cloud Datastore").asEntity("sampleTask")
```

### Create a Query
```scala
// Google's Java library
val query = Query.newEntityQueryBuilder()
  .setKind("Task")
  .setFilter(
    CompositeFilter.and(
      PropertyFilter.eq("done", false),
      PropertyFilter.ge("priority", 4)
    )
  )
  .setOrderBy(OrderBy.desc("priority"))
  .build()

// store4s
val query = Query.from[Task]
  .filter(t => !t.done && t.priority >= 4)
  .sortBy(_.priority.desc)
```

## Installation

```scala
"net.pishen" %% "store4s-rpc" % "<version>"
```

Java 11 is required since version 0.16.0.

store4s uses ScalaPB to connect with [Datastore's RPC API](https://cloud.google.com/datastore/docs/reference/data/rpc). Returning `Future` for each client request.

## Getting Started
```scala
import store4s.rpc._

val ds = Datastore()
```

store4s will detect the default project id using Google's [Application Default Credentials](https://cloud.google.com/docs/authentication/application-default-credentials), but you can also specify it by yourself:

```scala
val ds = Datastore(projectId = "my-project-id")
```

## Operations
Here are some basic functions to interact with Datastore:
```scala
ds.insert(entity)
ds.upsert(entity)
ds.update(entity)
ds.deleteById[Task](taskId)
ds.deleteByName[Task](taskName)
ds.lookupById[Task](taskId)
ds.lookupByName[Task](taskName)
ds.runQuery(query)
// ds.transaction coming soon
```

Note that all the operations return a `Future` immediately.

## Encoding
Convert a case class to Entity using `asEntity`:
```scala
case class Task(
  category: String,
  done: Boolean,
  priority: Int,
  description: String
)

// create an Entity with name
val entity1 = Task("Personal", false, 4, "Learn Cloud Datastore").asEntity("sampleTask")

// create an Entity with id
val entity2 = Task("Work", true, 5, "Drink milk").asEntity(10)
```
The basic data types, `Seq`, `Option`, and nested case classes are supported.

### Custom types
To support custom types, one can create an `Encoder` from an existing `Encoder` using `contramap`:
```scala
implicit val enc: Encoder[LocalDate] =
  Encoder.string.contramap[LocalDate](_.toString)
```

### Exclude from indexes
To exclude properties from indexes, use the `excludeFromIndexes` function from `Encoder`:
```scala
implicit val enc: Encoder[Task] = Encoder.gen[Task].excludeFromIndexes(_.description)
```

### Key infering
One can let `Endoer[T]` compute the id or name of `Entity` from `T` using `withId` or `withName`:
```scala
case class User(id: Long, name: String)
implicit val enc: Encoder[User] = Encoder.gen[User].withId(u => u.id)

User(10, "Pishen").asEntity
// Equal to User(10, "Pishen").asEntity(10)
```

## Decoding
Decode an Entity back to case class using `as[T]`:

```scala
entity.as[Task]
// Task("Personal", false, 4, "Learn Cloud Datastore")
```

> [!NOTE]
> By using helper functions like `lookupById[T]`, `lookupByName[T]`, and `runQuery`, `Decoder[T]` is automatically applied underneath, which means you usually don't need to call `as[T]` by yourself.

### Custom types
To support custom types, one can create a `Decoder` from an existing `Decoder` using `map`:
```scala
implicit val dec: Decoder[LocalDate] =
  Decoder.string.map(LocalDate.parse)
```

## Query
Build a Query object using `Query.from[A]`:
```scala
val query = Query.from[Task]
  .filter(t => !t.done && t.priority >= 4)
  .sortBy(_.priority.desc)
  .drop(2)
  .take(3)
```

Drop this Query object into `runQuery` to get the result:
```scala
ds.runQuery(query).map { res =>
  res.toSeq // Seq[Task]
  res.endCursor // ByteString
}
```

One can also trigger the run function directly from query object:
```scala
query.run(ds).map { res =>
  res.toSeq // Seq[Task]
  res.endCursor // ByteString
}
```

### Array type
For querying on [array type values](https://cloud.google.com/datastore/docs/concepts/queries#multiple_equality_filters), which corresponds to `Seq`, an `exists` function is available:
```scala
case class Task(tags: Seq[String])

Query.from[Task]
  .filter(_.tags.exists(_ == "Scala"))
  .filter(_.tags.exists(_ == "rocks"))
```

### Nested entity
For querying on the properties of embedded entity (which can be referred using `.`):
```scala
case class Category(name: String, description: String)
case class Task(category: Category, done: Boolean, description: String)

Query.from[Task].filter(_.category.name == "Personal")
```

## Java Conversions
For some libraries which requires the Java version protocol buffer classes, for example [Apache Beam](https://beam.apache.org/releases/javadoc/current/org/apache/beam/sdk/io/gcp/datastore/DatastoreIO.html) and [Scio](https://spotify.github.io/scio/io/Datastore.html), we can use `fromJavaProto` and `toJavaProto` to convert between Scala and Java versions:
```scala
// import Scala's Entity
import com.google.datastore.v1.entity.Entity
val sTask: Entity = Task(...).asEntity("taskName")
// convert it to Java's Entity
val jTask: com.google.datastore.v1.Entity = Entity.toJavaProto(sTask)

// we can now interact with Scio
val entities: SCollection[com.google.datastore.v1.Entity] =
  sc.parallelize(Seq(jTask))
entities.saveAsDatastore("project-id")

// it also works with Query
val query = Query.from[Task].toJavaProto
val res: SCollection[Task] = sc
  .datastore("project-id", query)
  .map(e => Entity.fromJavaProto(e).as[Task])
```



## Transaction
Coming soon

## Emulator
Coming soon
