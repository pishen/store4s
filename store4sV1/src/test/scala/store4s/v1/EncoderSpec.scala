package store4s.v1

import com.google.datastore.v1.ArrayValue
import com.google.datastore.v1.Entity
import com.google.datastore.v1.Key
import com.google.datastore.v1.PartitionId
import com.google.datastore.v1.Value
import com.google.datastore.v1.client.DatastoreOptions
import com.google.protobuf.NullValue
import org.scalatest.OneInstancePerTest
import org.scalatest.flatspec.AnyFlatSpec

import java.time.LocalDate
import scala.jdk.CollectionConverters._

class EncoderSpec extends AnyFlatSpec with OneInstancePerTest {
  val options = new DatastoreOptions.Builder().projectId("store4s").build()
  implicit val datastore = Datastore(options)

  def entityBuilder(kind: String) = Entity
    .newBuilder()
    .setKey(
      Key
        .newBuilder()
        .setPartitionId(
          PartitionId
            .newBuilder()
            .setProjectId("store4s")
            .build()
        )
        .addPath(
          Key.PathElement
            .newBuilder()
            .setKind(kind)
            .setName("entityName")
        )
    )

  "An v1.EntityEncoder" should "generate same output as Google Cloud Java" in {
    val userG = entityBuilder("User")
      .putProperties("id", Value.newBuilder().setIntegerValue(1).build())
      .putProperties(
        "name",
        Value.newBuilder().setStringValue("Sakura Minamoto").build()
      )
      .putProperties("admin", Value.newBuilder().setBooleanValue(true).build())
      .build()

    case class User(id: Int, name: String, admin: Boolean)
    val userS = User(1, "Sakura Minamoto", true).asEntity("entityName")

    assert(userG == userS)
  }

  it should "support nullable value" in {
    val userG = entityBuilder("User")
      .putProperties(
        "name",
        Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build()
      )
      .build()

    case class User(name: Option[String])
    val userS = User(None).asEntity("entityName")

    assert(userG == userS)
  }

  it should "support list value" in {
    val list = Seq("A", "B", "C")
    val arrayValue = ArrayValue
      .newBuilder()
      .addAllValues(
        list
          .map(e => Value.newBuilder().setStringValue(e).build())
          .asJava
      )
      .build()
    val groupG = entityBuilder("Group")
      .putProperties("id", Value.newBuilder().setIntegerValue(1).build())
      .putProperties(
        "members",
        Value.newBuilder().setArrayValue(arrayValue).build()
      )
      .build()

    case class Group(id: Int, members: Seq[String])
    val groupS = Group(1, list).asEntity("entityName")

    assert(groupG == groupS)
  }

  it should "support nested entity" in {
    val hometown = Entity
      .newBuilder()
      .putProperties(
        "country",
        Value.newBuilder().setStringValue("Japan").build()
      )
      .putProperties(
        "region",
        Value.newBuilder().setStringValue("Kyushu").build()
      )
      .putProperties(
        "city",
        Value.newBuilder().setStringValue("Saga").build()
      )
      .build()
    val userG = entityBuilder("User")
      .putProperties(
        "name",
        Value.newBuilder().setStringValue("Sakura Minamoto").build()
      )
      .putProperties(
        "hometown",
        Value.newBuilder().setEntityValue(hometown).build()
      )
      .build()

    case class Hometown(country: String, region: String, city: String)
    case class User(name: String, hometown: Hometown)
    val userS = User(
      "Sakura Minamoto",
      Hometown("Japan", "Kyushu", "Saga")
    ).asEntity("entityName")

    assert(userG == userS)
  }

  it should "support excludeFromIndexes" in {
    val description =
      "A high school girl and aspiring idol who dies in 2008 after being hit by a truck following a life filled with misfortune."

    val zG = entityBuilder("Zombie")
      .putProperties("number", Value.newBuilder().setIntegerValue(1).build())
      .putProperties(
        "name",
        Value.newBuilder().setStringValue("Sakura Minamoto").build()
      )
      .putProperties(
        "description",
        Value
          .newBuilder()
          .setStringValue(description)
          .setExcludeFromIndexes(true)
          .build()
      )
      .build()

    case class Zombie(number: Int, name: String, description: String)
    implicit val encoder =
      EntityEncoder[Zombie].excludeFromIndexes(_.description)
    val zS = Zombie(1, "Sakura Minamoto", description).asEntity("entityName")

    assert(zG == zS)
  }

  it should "support excludeFromIndexes on list value" in {
    val list = Seq("A", "B", "C")
    val arrayValue = ArrayValue
      .newBuilder()
      .addAllValues(
        list
          .map(e =>
            Value
              .newBuilder()
              .setStringValue(e)
              .setExcludeFromIndexes(true)
              .build()
          )
          .asJava
      )
      .build()
    val groupG = entityBuilder("Group")
      .putProperties("id", Value.newBuilder().setIntegerValue(1).build())
      .putProperties(
        "members",
        Value.newBuilder().setArrayValue(arrayValue).build()
      )
      .build()

    case class Group(id: Int, members: Seq[String])
    implicit val encoder = EntityEncoder[Group].excludeFromIndexes(_.members)
    val groupS = Group(1, list).asEntity("entityName")

    assert(groupG == groupS)
  }

  it should "support ADT" in {
    sealed trait Member
    case class Zombie(number: Int, name: String, died: String) extends Member
    case class Human(number: Int, name: String) extends Member

    val hG = entityBuilder("Member")
      .putProperties("number", Value.newBuilder().setIntegerValue(7).build())
      .putProperties(
        "name",
        Value.newBuilder().setStringValue("Maimai Yuzuriha").build()
      )
      .putProperties(
        "_type",
        Value.newBuilder().setStringValue("Human").build()
      )
      .build()

    val member: Member = Human(7, "Maimai Yuzuriha")
    val hS = member.asEntity("entityName")

    assert(hG == hS)
  }

  it should "support asEntity(A => B)" in {
    val zG = Entity
      .newBuilder()
      .setKey(
        Key
          .newBuilder()
          .setPartitionId(
            PartitionId
              .newBuilder()
              .setProjectId("store4s")
          )
          .addPath(
            Key.PathElement
              .newBuilder()
              .setKind("Zombie")
              .setName("(1,Sakura Minamoto)")
          )
      )
      .putProperties("number", Value.newBuilder().setIntegerValue(1).build())
      .putProperties(
        "name",
        Value.newBuilder().setStringValue("Sakura Minamoto").build()
      )
      .build()

    case class Zombie(number: Int, name: String)
    val zS = Zombie(1, "Sakura Minamoto").asEntity(z => (z.number, z.name))

    assert(zG == zS)
  }

  "A v1.ValueEncoder" should "support contramap" in {
    val userG = entityBuilder("User")
      .putProperties(
        "name",
        Value.newBuilder().setStringValue("Sakura Minamoto").build()
      )
      .putProperties(
        "birthday",
        Value.newBuilder().setStringValue("1991-04-02").build()
      )
      .build()

    implicit val enc =
      ValueEncoder.stringEncoder.contramap[LocalDate](_.toString())

    case class User(name: String, birthday: LocalDate)
    val userS =
      User("Sakura Minamoto", LocalDate.of(1991, 4, 2)).asEntity("entityName")

    assert(userG == userS)
  }
}
