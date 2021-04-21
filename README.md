# Scalastore

A Scala library for Google Cloud Datastore

```scala
case class Task(category: String, done: Boolean, priority: Int, description: String)

//google-cloud-java
FullEntity.newBuilder(keyFactory.setKind("Task"))
  .set("category", "Personal")
  .set("done", false)
  .set("priority", 4)
  .set("description", "Learn Cloud Datastore")
  .build()

//scalastore
Task("Personal", false, 4, "Learn Cloud Datastore").asEntity

//google-cloud-java
Task(
  entity.getString("category"),
  entity.getBoolean("done"),
  entity.getLong("priority").toInt,
  entity.getString("description")
)

//scalastore
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

//scalastore
Query.from[Task]
  .filter(_.done == false)
  .filter(_.priority >= 4)
  .sortBy(_.priority)
```
