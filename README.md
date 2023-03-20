# store4s

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/net.pishen/store4s_2.13/badge.svg)](https://maven-badges.herokuapp.com/maven-central/net.pishen/store4s_2.13)
[![javadoc](https://javadoc.io/badge2/net.pishen/store4s_2.13/javadoc.svg)](https://javadoc.io/doc/net.pishen/store4s_2.13)

A Scala library for [Firestore in Datastore mode](https://cloud.google.com/datastore), providing compile-time mappings between case classes and Datastore entities, a type-safe query DSL, and asynchronous interfaces.

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

> **Note**
> This document is for the sttp version of store4s, to see the older version which is integrated with Google's Java API and Datastore V1 API (which is compatible with Apache Beam), check the [old README](https://github.com/pishen/store4s/tree/v0.14.0).

```scala
"net.pishen" %% "store4s-sttp" % "<version>"
```

store4s uses [sttp](https://sttp.softwaremill.com/en/stable/index.html) to connect with [Datastore's REST API](https://cloud.google.com/datastore/docs/reference/data/rest/v1/projects). By using sttp, you can integrate store4s with the [HTTP backend](https://sttp.softwaremill.com/en/stable/backends/summary.html) and [JSON library](https://sttp.softwaremill.com/en/stable/json.html) you are using. (e.g. akka-http with circe, or http4s with play-json ...etc)

If you are using circe as your JSON library, add the additional dependency to reduce the boilerplate code:

```scala
"net.pishen" %% "store4s-sttp-circe" % "<version>"
```

## Getting Started
```scala
import store4s.sttp._
// import these if you are using circe
import store4s.sttp.circe._
import sttp.client3.circe._
import io.circe.generic.auto._

// synchronous version
implicit val ds = Datastore()

// asynchronous version (use akka-http as example)
import sttp.client3.akkahttp._
implicit val ds = Datastore(backend = AkkaHttpBackend())
```

store4s will detect the default project id and refresh the access token using Google's [Application Default Credentials](https://cloud.google.com/docs/authentication/application-default-credentials), but you can also specify it by yourself:

```scala
implicit val ds = Datastore(projectId = "my-project-id")
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
ds.transaction { tx =>
  val oldTask = tx.lookupById[Task](taskId).get
  val entity = changeTaskInfo(oldTask)
  (oldTask, Seq(tx.update(entity)))
}
```

Check the [Scaladoc](https://javadoc.io/doc/net.pishen/store4s_2.13) or read on for further details.

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
To support custom types, one can create a `ValueEncoder` from an existing `ValueEncoder` using `contramap`:
```scala
implicit val enc: ValueEncoder[LocalDate] =
  ValueEncoder.stringEncoder.contramap[LocalDate](_.toString)
```

### Exclude from indexes
To exclude properties from indexes, use the `excludeFromIndexes` function from `EntityEncoder`:
```scala
implicit val enc = EntityEncoder[Task].excludeFromIndexes(_.description)
```

## Decoding
Decode an Entity back to case class using `EntityDecoder`:

```scala
EntityDecoder[Task].decode(entity)
// Right(Task("Personal", false, 4, "Learn Cloud Datastore"))
```

> **Note**
> By using helper functions like `lookupById[A]`, `lookupByName[A]`, and `runQuery` from `Datastore`, `EntityDecoder` is automatically applied underneath, which means you usually don't need to call this `decode` function by yourself.

### Custom types
To support custom types, one can create a `ValueDecoder` from an existing `ValueDecoder` using `map` or `emap`:
```scala
implicit val dec: ValueDecoder[LocalDate] =
  ValueDecoder.stringDecoder.map(LocalDate.parse)
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
val res = ds.runQuery(query)

res.toSeq // Seq[Task]
res.endCursor // String
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

## ADT (Algebraic Data Types)

Support for encoding/decoding ADT is achieved by adding a property named `_type` into entities. When encoding a trait like this:

```scala
sealed trait User
case class Student(name: String) extends User
case class Teacher(name: String) extends User

val user: User = Student("Maimai Yuzuriha")

val entity = user.asEntity("sampleUser")
```

The result entity will be:

```
key {
  path {
    kind: "User"
    name: "sampleUser"
  }
}
properties {
  key: "_type"
  value {
    string_value: "Student"
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
EntityDecoder[User].decode(entity)
```

The property name `_type` can be configured by providing your own `TypeIdentifier`:

```scala
implicit val typeIdentifier = TypeIdentifier("my_type")
```

## Transaction

Use `transaction` to create a Transaction:

```scala
val res = ds.transaction { tx =>
  val oldTask = tx.lookupById[Task](taskId).get
  val entity = changeTaskInfo(oldTask)
  (oldTask, Seq(tx.update(entity)))
}
// res: Task
```

It expects a lambda function with type `Transaction[F] => F[(R, Seq[Mutation])]`, note that all the mutations should be committed together at the end. If the lambda return a failed effect (e.g. throwing an Exception in synchronous mode or returning a failed Future in asynchronous mode), transaction will be automatically rolled back and no changes will be applied.
