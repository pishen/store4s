package store4s

import com.google.cloud.datastore.Entity
import com.google.cloud.datastore.FullEntity
import com.google.cloud.datastore.KeyFactory
import com.google.datastore.v1
import org.scalatest.flatspec.AnyFlatSpec

class EncoderSpec extends AnyFlatSpec {
  def keyFactory = new KeyFactory("zombie-land-saga")
  implicit val keyCtx = KeyContext("zombie-land-saga", None)

  "An EntityEncoder" should "generate same output as Google Cloud Java" in {
    val zG = Entity
      .newBuilder(keyFactory.setKind("Zombie").newKey("heroine"))
      .set("number", 1)
      .set("name", "Sakura Minamoto")
      .set("girl", true)
      .build()

    case class Zombie(number: Int, name: String, girl: Boolean)
    val zS = Zombie(1, "Sakura Minamoto", true).asEntity("heroine")

    assert(zG == zS)
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
      .set("country", "Japan")
      .set("city", "Saga")
      .build()
    val zG = FullEntity
      .newBuilder(keyFactory.setKind("Zombie").newKey())
      .set("name", "Sakura")
      .set("hometown", hometown)
      .build()

    case class Hometown(country: String, city: String)
    case class Zombie(name: String, hometown: Hometown)
    val zS = Zombie("Sakura", Hometown("Japan", "Saga")).asEntity

    assert(zG == zS)
  }

  it should "support v1 entity" in {
    val partitionId = v1.PartitionId
      .newBuilder()
      .setProjectId("zombie-land-saga")
    val path = v1.Key.PathElement.newBuilder().setKind("Zombie")
    val key = v1.Key.newBuilder().setPartitionId(partitionId).addPath(path)
    val zG = v1.Entity
      .newBuilder()
      .setKey(key)
      .putProperties(
        "name",
        v1.Value.newBuilder().setStringValue("Sakura").build()
      )
      .build()

    case class Zombie(name: String)
    val zS = Zombie("Sakura").asEntity.toV1

    assert(zG == zS)
  }
}
