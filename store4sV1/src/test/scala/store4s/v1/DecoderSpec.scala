package store4s.v1

import com.google.datastore.v1.ArrayValue
import com.google.datastore.v1.Entity
import com.google.datastore.v1.Key
import com.google.datastore.v1.PartitionId
import com.google.datastore.v1.Value
import com.google.datastore.v1.client.DatastoreOptions
import com.google.protobuf.NullValue
import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec

import java.time.LocalDate
import scala.jdk.CollectionConverters._

class DecoderSpec extends AnyFlatSpec with EitherValues {
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

  "An v1.EntityDecoder" should "decode Entity into case class" in {
    val userG = entityBuilder("User")
      .putProperties(
        "id",
        Value.newBuilder().setIntegerValue(1).build()
      )
      .putProperties(
        "name",
        Value.newBuilder().setStringValue("Sakura Minamoto").build()
      )
      .putProperties(
        "admin",
        Value.newBuilder().setBooleanValue(true).build()
      )
      .build()

    case class User(id: Int, name: String, admin: Boolean)
    val userS = User(1, "Sakura Minamoto", true)

    assert(decodeEntity[User](userG) == Right(userS))
  }

  it should "get an Exception for unmatched type" in {
    val e = entityBuilder("User")
      .putProperties("id", Value.newBuilder().setStringValue("abc").build())
      .build()
    case class User(id: Int)

    assert(decodeEntity[User](e).left.value.isInstanceOf[Exception])
  }

  it should "get an IllegalArgumentException for not found property" in {
    val e = entityBuilder("User")
      .putProperties("id", Value.newBuilder().setIntegerValue(1).build())
      .build()
    case class User(name: String)

    assert(
      decodeEntity[User](e).left.value.isInstanceOf[IllegalArgumentException]
    )
  }

  it should "support nullable value" in {
    val userG = entityBuilder("User")
      .putProperties(
        "name",
        Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build()
      )
      .build()

    case class User(name: Option[String], age: Option[Int])
    val userS = User(None, None)

    assert(decodeEntity[User](userG) == Right(userS))
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
    val groupS = Group(1, list)

    assert(decodeEntity[Group](groupG) == Right(groupS))
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
        Value.newBuilder().setStringValue("Sakura").build()
      )
      .putProperties(
        "hometown",
        Value.newBuilder().setEntityValue(hometown).build()
      )
      .build()

    case class Hometown(country: String, region: String, city: String)
    case class User(name: String, hometown: Hometown)
    val userS = User("Sakura", Hometown("Japan", "Kyushu", "Saga"))

    assert(decodeEntity[User](userG) == Right(userS))
  }

  it should "support ADT" in {
    sealed trait Member
    case class Zombie(number: Int, name: String, died: String) extends Member
    case class Human(number: Int, name: String) extends Member

    val hG = Entity
      .newBuilder()
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

    assert(decodeEntity[Member](hG) == Right(Human(7, "Maimai Yuzuriha")))
  }

  "A v1.ValueDecoder" should "support map" in {
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

    implicit val dec = ValueDecoder.stringDecoder.map(LocalDate.parse)

    case class User(name: String, birthday: LocalDate)
    val userS = User("Sakura Minamoto", LocalDate.of(1991, 4, 2))

    assert(decodeEntity[User](userG) == Right(userS))
  }
}
