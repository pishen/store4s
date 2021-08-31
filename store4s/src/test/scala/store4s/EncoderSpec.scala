package store4s

import com.google.cloud.datastore.Entity
import com.google.cloud.datastore.FullEntity
import com.google.cloud.datastore.KeyFactory
import com.google.cloud.datastore.{Datastore => GDatastore}
import org.scalamock.scalatest.MockFactory
import org.scalatest.OneInstancePerTest
import org.scalatest.flatspec.AnyFlatSpec

class EncoderSpec extends AnyFlatSpec with OneInstancePerTest with MockFactory {
  val mockGDatastore = mock[GDatastore]
  val keyFactory = new KeyFactory("store4s")
  (mockGDatastore.newKeyFactory _).expects().returning(keyFactory)

  implicit val datastore = Datastore(mockGDatastore)

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
}
