package store4s

import com.google.cloud.datastore.Entity
import com.google.cloud.datastore.FullEntity
import com.google.cloud.datastore.KeyFactory
import com.google.datastore.v1
import org.scalatest.flatspec.AnyFlatSpec

class EncoderSpec extends AnyFlatSpec {
  def keyFactory = new KeyFactory("my-project")
  implicit val keyCtx = KeyContext("my-project", None)

  "An EntityEncoder" should "generate same output as Google Cloud Java" in {
    val taskKey = keyFactory.setKind("Task").newKey("sampleTask")
    val taskG = Entity
      .newBuilder(taskKey)
      .set("category", "Personal")
      .set("done", false)
      .set("priority", 4)
      .set("description", "Learn Cloud Datastore")
      .build()

    case class Task(
        category: String,
        done: Boolean,
        priority: Int,
        description: String
    )
    val taskS =
      Task("Personal", false, 4, "Learn Cloud Datastore").asEntity("sampleTask")

    assert(taskG == taskS)
  }

  it should "support IncompleteKey" in {
    val key = keyFactory.setKind("User").newKey()
    val userG = FullEntity.newBuilder(key).set("name", "John").build()

    case class User(name: String)
    val userS = User("John").asEntity

    assert(userG == userS)
  }

  it should "support nullable value" in {
    val key = keyFactory.setKind("User").newKey()
    val userG = FullEntity.newBuilder(key).setNull("name").build()

    case class User(name: Option[String])
    val userS = User(None).asEntity

    assert(userG == userS)
  }

  it should "support list value" in {
    val key = keyFactory.setKind("Group").newKey()
    val groupG = FullEntity
      .newBuilder(key)
      .set("id", 1)
      .set("members", "A", "B", "C")
      .build()

    case class Group(id: Int, members: Seq[String])
    val groupS = Group(1, Seq("A", "B", "C")).asEntity

    assert(groupG == groupS)
  }

  it should "support nested entity" in {
    val hometown = FullEntity
      .newBuilder()
      .set("country", "Taiwan")
      .set("city", "Tainan")
      .build()
    val userG = FullEntity
      .newBuilder(keyFactory.setKind("User").newKey())
      .set("name", "Pishen")
      .set("hometown", hometown)
      .build()

    case class Hometown(country: String, city: String)
    case class User(name: String, hometown: Hometown)
    val userS = User("Pishen", Hometown("Taiwan", "Tainan")).asEntity

    assert(userG == userS)
  }

  it should "support v1 entity" in {
    val partitionId = v1.PartitionId.newBuilder().setProjectId("my-project")
    val path = v1.Key.PathElement.newBuilder().setKind("User")
    val key = v1.Key.newBuilder().setPartitionId(partitionId).addPath(path)
    val userG = v1.Entity
      .newBuilder()
      .setKey(key)
      .putProperties(
        "name",
        v1.Value.newBuilder().setStringValue("John").build()
      )
      .build()

    case class User(name: String)
    val userS = User("John").asEntity.toV1

    assert(userG == userS)
  }
}
