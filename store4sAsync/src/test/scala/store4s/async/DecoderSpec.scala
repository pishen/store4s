package store4s.async

import org.scalatest.flatspec.AnyFlatSpec
import store4s.async.model._

import java.time.LocalDate

class DecoderSpec extends AnyFlatSpec {
  val partitionId = PartitionId("store4s", None)

  "An EntityDecoder" should "decode Entity into case class" in {
    case class Zombie(number: Int, name: String, girl: Boolean)
    val ans = Zombie(1, "Sakura Minamoto", true)
    val entity = Entity(
      Some(Key(partitionId, Seq(PathElement("Zombie", None, Some("heroine"))))),
      Map(
        "number" -> Value(false, integerValue = Some("1")),
        "name" -> Value(false, stringValue = Some("Sakura Minamoto")),
        "girl" -> Value(false, booleanValue = Some(true))
      )
    )
    assert(EntityDecoder[Zombie].decode(entity) == Right(ans))
  }

  it should "get ValueDecodeError for unmatched type" in {
    val entity = Entity(
      Some(Key(partitionId, Seq(PathElement("User", None, None)))),
      Map("id" -> Value(false, stringValue = Some("abc")))
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
      Map("id" -> Value(false, integerValue = Some("1")))
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
      Map("name" -> Value(false, nullValue = Some("NULL_VALUE")))
    )
    assert(EntityDecoder[User].decode(entity) == Right(ans))
  }

  it should "support list value" in {
    case class Group(id: Int, members: Seq[String])
    val ans = Group(1, Seq("A", "B", "C"))
    val entity = Entity(
      Some(Key(partitionId, Seq(PathElement("Group", None, None)))),
      Map(
        "id" -> Value(false, integerValue = Some("1")),
        "members" -> Value(
          false,
          arrayValue = Some(
            ArrayValue(
              Seq(
                Value(false, stringValue = Some("A")),
                Value(false, stringValue = Some("B")),
                Value(false, stringValue = Some("C"))
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
        "country" -> Value(false, stringValue = Some("Japan")),
        "region" -> Value(false, stringValue = Some("Kyushu")),
        "city" -> Value(false, stringValue = Some("Saga"))
      )
    )
    val entity = Entity(
      Some(Key(partitionId, Seq(PathElement("Zombie", None, None)))),
      Map(
        "name" -> Value(false, stringValue = Some("Sakura")),
        "hometown" -> Value(false, entityValue = Some(hometown))
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
        "number" -> Value(false, integerValue = Some("7")),
        "name" -> Value(false, stringValue = Some("Maimai Yuzuriha")),
        "_type" -> Value(false, stringValue = Some("Human"))
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
        "name" -> Value(false, stringValue = Some("Sakura Minamoto")),
        "birthday" -> Value(false, stringValue = Some("1991-04-02"))
      )
    )
    assert(EntityDecoder[Zombie].decode(entity) == Right(ans))
  }
}
