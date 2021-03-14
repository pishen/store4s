# scalastore

```scala
case class User(name: String, age: Int)

FullEntity.newBuilder(keyFactory.setKind("User"))
  .set("name", user.name)
  .set("age", user.age)
  .build()

Encoder[User].encode(user)
```
