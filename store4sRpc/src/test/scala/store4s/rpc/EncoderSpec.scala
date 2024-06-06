package store4s.rpc

import com.google.datastore.v1.entity.ArrayValue
import com.google.datastore.v1.entity.Entity
import com.google.datastore.v1.entity.Key
import com.google.datastore.v1.entity.Key.PathElement
import com.google.datastore.v1.entity.PartitionId
import com.google.datastore.v1.entity.Value
import com.google.protobuf.struct.NullValue
import org.scalatest.flatspec.AnyFlatSpec
import java.time.LocalDate

class EncoderSpec extends AnyFlatSpec {
  "An Encoder" should "encode case class into Entity" in {
    val ans = Entity()
      .withKey(
        Key()
          .withPartitionId(PartitionId(projectId = Datastore.defaultProjectId))
          .addPath(PathElement(kind = "Zombie").withName("heroine"))
      )
      .addProperties(
        "number" -> Value().withIntegerValue(1),
        "name" -> Value().withStringValue("Sakura Minamoto"),
        "girl" -> Value().withBooleanValue(true)
      )
    case class Zombie(number: Int, name: String, girl: Boolean)
    val res = Zombie(1, "Sakura Minamoto", true).asEntity("heroine")
    assert(res == ans)
  }

  it should "support nullable value" in {
    val ans = Entity()
      .withKey(
        Key()
          .withPartitionId(PartitionId(projectId = Datastore.defaultProjectId))
          .addPath(PathElement(kind = "User").withId(1))
      )
      .addProperties("name" -> Value().withNullValue(NullValue.NULL_VALUE))
    case class User(name: Option[String])
    val res = User(None).asEntity(1)
    assert(res == ans)
  }

  it should "support array value" in {
    val ans = Entity()
      .withKey(
        Key()
          .withPartitionId(PartitionId(projectId = Datastore.defaultProjectId))
          .addPath(PathElement(kind = "Group").withId(1))
      )
      .addProperties(
        "id" -> Value().withIntegerValue(1),
        "members" -> Value().withArrayValue(
          ArrayValue().addValues(
            Value().withStringValue("A"),
            Value().withStringValue("B"),
            Value().withStringValue("C")
          )
        )
      )
    case class Group(id: Int, members: Seq[String])
    val res = Group(1, Seq("A", "B", "C")).asEntity(1)
    assert(res == ans)
  }

  it should "support nested entity" in {
    val hometown = Entity().addProperties(
      "country" -> Value().withStringValue("Japan"),
      "region" -> Value().withStringValue("Kyushu"),
      "city" -> Value().withStringValue("Saga")
    )
    val ans = Entity()
      .withKey(
        Key()
          .withPartitionId(PartitionId(projectId = Datastore.defaultProjectId))
          .addPath(PathElement(kind = "Zombie").withId(1))
      )
      .addProperties(
        "name" -> Value().withStringValue("Sakura"),
        "hometown" -> Value().withEntityValue(hometown)
      )
    case class Hometown(country: String, region: String, city: String)
    case class Zombie(name: String, hometown: Hometown)
    val res = Zombie("Sakura", Hometown("Japan", "Kyushu", "Saga")).asEntity(1)
    assert(res == ans)
  }

  it should "support excludeFromIndexes" in {
    val ans = Entity()
      .withKey(
        Key()
          .withPartitionId(PartitionId(projectId = Datastore.defaultProjectId))
          .addPath(PathElement(kind = "Zombie").withName("heroine"))
      )
      .addProperties(
        "number" -> Value().withIntegerValue(1),
        "name" -> Value().withStringValue("Sakura Minamoto"),
        "description" -> Value(excludeFromIndexes = true)
          .withStringValue("saga" * 500)
      )

    case class Zombie(number: Int, name: String, description: String)
    implicit val encoder: Encoder[Zombie] =
      Encoder.gen[Zombie].excludeFromIndexes(_.description)
    val res = Zombie(1, "Sakura Minamoto", "saga" * 500).asEntity("heroine")
    assert(res == ans)
  }

  it should "support excludeFromIndexes on array value" in {
    val ans = Entity()
      .withKey(
        Key()
          .withPartitionId(PartitionId(projectId = Datastore.defaultProjectId))
          .addPath(PathElement(kind = "Group").withId(1))
      )
      .addProperties(
        "id" -> Value().withIntegerValue(1),
        "members" -> Value().withArrayValue(
          ArrayValue().addValues(
            Value(excludeFromIndexes = true).withStringValue("A"),
            Value(excludeFromIndexes = true).withStringValue("B"),
            Value(excludeFromIndexes = true).withStringValue("C")
          )
        )
      )
    case class Group(id: Int, members: Seq[String])
    implicit val encoder: Encoder[Group] =
      Encoder.gen[Group].excludeFromIndexes(_.members)
    val res = Group(1, Seq("A", "B", "C")).asEntity(1)
    assert(res == ans)
  }

  it should "support excludeFromIndexes on nested entity" in {
    val hometown = Entity().addProperties(
      "country" -> Value().withStringValue("Japan"),
      "region" -> Value().withStringValue("Kyushu"),
      "city" -> Value(excludeFromIndexes = true).withStringValue("Saga")
    )
    val ans = Entity()
      .withKey(
        Key()
          .withPartitionId(PartitionId(projectId = Datastore.defaultProjectId))
          .addPath(PathElement(kind = "Zombie").withId(1))
      )
      .addProperties(
        "name" -> Value().withStringValue("Sakura"),
        "hometown" -> Value().withEntityValue(hometown)
      )
    case class Hometown(country: String, region: String, city: String)
    case class Zombie(name: String, hometown: Hometown)
    implicit val encoder: Encoder[Hometown] =
      Encoder.gen[Hometown].excludeFromIndexes(_.city)
    val res = Zombie("Sakura", Hometown("Japan", "Kyushu", "Saga")).asEntity(1)
    assert(res == ans)
  }

  it should "support withName" in {
    val ans = Entity()
      .withKey(
        Key()
          .withPartitionId(PartitionId(projectId = Datastore.defaultProjectId))
          .addPath(PathElement(kind = "Zombie").withName("Sakura Minamoto"))
      )
      .addProperties(
        "number" -> Value().withIntegerValue(1),
        "name" -> Value().withStringValue("Sakura Minamoto"),
        "girl" -> Value().withBooleanValue(true)
      )
    case class Zombie(number: Int, name: String, girl: Boolean)
    implicit val encoder: Encoder[Zombie] = Encoder.gen[Zombie].withName(_.name)
    val res = Zombie(1, "Sakura Minamoto", true).asEntity
    assert(res == ans)
  }

  it should "support contramap" in {
    val ans = Entity()
      .withKey(
        Key()
          .withPartitionId(PartitionId(projectId = Datastore.defaultProjectId))
          .addPath(PathElement(kind = "Zombie").withId(1))
      )
      .addProperties(
        "name" -> Value().withStringValue("Sakura Minamoto"),
        "birthday" -> Value().withStringValue("1991-04-02")
      )
    implicit val enc: Encoder[LocalDate] =
      Encoder.string.contramap[LocalDate](_.toString())
    case class Zombie(name: String, birthday: LocalDate)
    val res = Zombie("Sakura Minamoto", LocalDate.of(1991, 4, 2)).asEntity(1)
    assert(res == ans)
  }
}
