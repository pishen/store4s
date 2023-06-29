package store4s.sttp

import org.scalatest.flatspec.AnyFlatSpec
import store4s.sttp.model._

import java.time.LocalDate

class DecoderSpec extends AnyFlatSpec {
  val partitionId = PartitionId("store4s", None)

  "An EntityDecoder" should "decode Entity into case class" in {
    case class Zombie(number: Int, name: String, girl: Boolean)
    val ans = Zombie(1, "Sakura Minamoto", true)
    val entity = Entity(
      Some(Key(partitionId, Seq(PathElement("Zombie", None, Some("heroine"))))),
      Map(
        "number" -> Value(integerValue = Some("1")),
        "name" -> Value(stringValue = Some("Sakura Minamoto")),
        "girl" -> Value(booleanValue = Some(true))
      )
    )
    assert(EntityDecoder[Zombie].decode(entity) == Right(ans))
  }

  it should "get ValueDecodeError for unmatched type" in {
    val entity = Entity(
      Some(Key(partitionId, Seq(PathElement("User", None, None)))),
      Map("id" -> Value(stringValue = Some("abc")))
    )
    case class User(id: Int)
    val error = intercept[ValueDecodeError] {
      EntityDecoder[User].decode(entity).toTry.get
    }
    assert(error.targetType == "Int")
  }

  it should "get EntityDecodeError for not found property" in {
    val entity = Entity(
      Some(Key(partitionId, Seq(PathElement("User", None, None)))),
      Map("id" -> Value(integerValue = Some("1")))
    )
    case class User(name: String)
    val error = intercept[EntityDecodeError] {
      EntityDecoder[User].decode(entity).toTry.get
    }
    assert(error.fieldName == "name")
  }

  it should "support nullable value" in {
    case class User(name: Option[String], age: Option[Int])
    val ans = User(None, None)
    val entity = Entity(
      Some(Key(partitionId, Seq(PathElement("User", None, None)))),
      Map("name" -> Value(excludeFromIndexes = Some(true)))
    )
    assert(EntityDecoder[User].decode(entity) == Right(ans))
  }

  it should "support list value" in {
    case class Group(id: Int, members: Seq[String])
    val ans = Group(1, Seq("A", "B", "C"))
    val entity = Entity(
      Some(Key(partitionId, Seq(PathElement("Group", None, None)))),
      Map(
        "id" -> Value(integerValue = Some("1")),
        "members" -> Value(
          arrayValue = Some(
            ArrayValue(
              Seq(
                Value(stringValue = Some("A")),
                Value(stringValue = Some("B")),
                Value(stringValue = Some("C"))
              )
            )
          )
        )
      )
    )
    assert(EntityDecoder[Group].decode(entity) == Right(ans))
  }

  it should "support nested entity" in {
    // we need 3 fields in Hometown to check diverging implicit (Lazy)
    case class Hometown(country: String, region: String, city: String)
    case class Zombie(name: String, hometown: Hometown)
    val ans = Zombie("Sakura", Hometown("Japan", "Kyushu", "Saga"))
    val hometown = Entity(
      None,
      Map(
        "country" -> Value(stringValue = Some("Japan")),
        "region" -> Value(stringValue = Some("Kyushu")),
        "city" -> Value(stringValue = Some("Saga"))
      )
    )
    val entity = Entity(
      Some(Key(partitionId, Seq(PathElement("Zombie", None, None)))),
      Map(
        "name" -> Value(stringValue = Some("Sakura")),
        "hometown" -> Value(entityValue = Some(hometown))
      )
    )
    assert(EntityDecoder[Zombie].decode(entity) == Right(ans))
  }

  it should "support ADT" in {
    sealed trait Member
    case class Zombie(number: Int, name: String, died: String) extends Member
    case class Human(number: Int, name: String) extends Member
    val ans = Human(7, "Maimai Yuzuriha")
    val entity = Entity(
      Some(Key(partitionId, Seq(PathElement("Member", None, None)))),
      Map(
        "number" -> Value(integerValue = Some("7")),
        "name" -> Value(stringValue = Some("Maimai Yuzuriha")),
        "_type" -> Value(stringValue = Some("Human"))
      )
    )
    assert(EntityDecoder[Member].decode(entity) == Right(ans))
  }

  "A ValueDecoder" should "support map" in {
    case class Zombie(name: String, birthday: LocalDate)
    val ans = Zombie("Sakura Minamoto", LocalDate.of(1991, 4, 2))
    implicit val dec = ValueDecoder.stringDecoder.map(LocalDate.parse)
    val entity = Entity(
      Some(Key(partitionId, Seq(PathElement("Zombie", None, None)))),
      Map(
        "name" -> Value(stringValue = Some("Sakura Minamoto")),
        "birthday" -> Value(stringValue = Some("1991-04-02"))
      )
    )
    assert(EntityDecoder[Zombie].decode(entity) == Right(ans))
  }
}
