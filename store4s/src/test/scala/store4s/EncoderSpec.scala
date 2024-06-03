package store4s

import com.google.cloud.datastore.Entity
import com.google.cloud.datastore.FullEntity
import com.google.cloud.datastore.KeyFactory
import com.google.cloud.datastore.StringValue
import com.google.cloud.datastore.{Datastore => GDatastore}
import org.scalamock.scalatest.MockFactory
import org.scalatest.OneInstancePerTest
import org.scalatest.flatspec.AnyFlatSpec

import java.time.LocalDate
import com.google.cloud.datastore.ListValue

class EncoderSpec extends AnyFlatSpec with OneInstancePerTest with MockFactory {
  val mockGDatastore = mock[GDatastore]
  val keyFactory = new KeyFactory("store4s")
  (mockGDatastore.newKeyFactory _).expects().returning(keyFactory)

  implicit val datastore: Datastore = Datastore(mockGDatastore)

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
      .set("region", "Kyushu")
      .set("city", "Saga")
      .build()
    val zG = FullEntity
      .newBuilder(keyFactory.setKind("Zombie").newKey())
      .set("name", "Sakura")
      .set("hometown", hometown)
      .build()

    // we need 3 fields in Hometown to check diverging implicit (Lazy)
    case class Hometown(country: String, region: String, city: String)
    case class Zombie(name: String, hometown: Hometown)
    val zS = Zombie("Sakura", Hometown("Japan", "Kyushu", "Saga")).asEntity

    assert(zG == zS)
  }

  it should "support excludeFromIndexes" in {
    val description =
      "A high school girl and aspiring idol who dies in 2008 after being hit by a truck following a life filled with misfortune."

    val zG = Entity
      .newBuilder(keyFactory.setKind("Zombie").newKey("heroine"))
      .set("number", 1)
      .set("name", "Sakura Minamoto")
      .set(
        "description",
        StringValue.newBuilder(description).setExcludeFromIndexes(true).build()
      )
      .build()

    case class Zombie(number: Int, name: String, description: String)
    implicit val encoder =
      EntityEncoder[Zombie].excludeFromIndexes(_.description)
    val zS = Zombie(1, "Sakura Minamoto", description).asEntity("heroine")

    assert(zG == zS)
  }

  it should "support excludeFromIndexes on list value" in {
    val key = keyFactory.setKind("Group").newKey()
    val groupG = FullEntity
      .newBuilder(key)
      .set("id", 1)
      .set(
        "members",
        ListValue.of(
          StringValue.newBuilder("A").setExcludeFromIndexes(true).build(),
          StringValue.newBuilder("B").setExcludeFromIndexes(true).build(),
          StringValue.newBuilder("C").setExcludeFromIndexes(true).build()
        )
      )
      .build()

    case class Group(id: Int, members: Seq[String])
    implicit val encoder = EntityEncoder[Group].excludeFromIndexes(_.members)
    val groupS = Group(1, Seq("A", "B", "C")).asEntity

    assert(groupG == groupS)
  }

  it should "support ADT" in {
    sealed trait Member
    case class Zombie(number: Int, name: String, died: String) extends Member
    case class Human(number: Int, name: String) extends Member

    val hG = FullEntity
      .newBuilder(keyFactory.setKind("Member").newKey())
      .set("number", 7)
      .set("name", "Maimai Yuzuriha")
      .set("_type", "Human")
      .build()

    val member: Member = Human(7, "Maimai Yuzuriha")
    val hS = member.asEntity

    assert(hG == hS)
  }

  it should "support asEntity(A => B)" in {
    val zG = Entity
      .newBuilder(keyFactory.setKind("Zombie").newKey("(1,Sakura Minamoto)"))
      .set("number", 1)
      .set("name", "Sakura Minamoto")
      .build()

    case class Zombie(number: Int, name: String)
    val zS = Zombie(1, "Sakura Minamoto").asEntity(z => (z.number, z.name))

    assert(zG == zS)
  }

  "A ValueEncoder" should "support contramap" in {
    val zG = FullEntity
      .newBuilder(keyFactory.setKind("Zombie").newKey())
      .set("name", "Sakura Minamoto")
      .set("birthday", "1991-04-02")
      .build()

    implicit val enc = ValueEncoder.stringEncoder
      .contramap[LocalDate](_.toString())

    case class Zombie(name: String, birthday: LocalDate)
    val zS = Zombie("Sakura Minamoto", LocalDate.of(1991, 4, 2)).asEntity

    assert(zG == zS)
  }
}
