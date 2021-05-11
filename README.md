# store4s

A Scala library for Google Cloud Datastore

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
