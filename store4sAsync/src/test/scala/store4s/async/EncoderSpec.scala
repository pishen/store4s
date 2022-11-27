package store4s.async

import org.scalatest.flatspec.AnyFlatSpec
import store4s.async.model._

import java.time.LocalDate

class EncoderSpec extends AnyFlatSpec {
  implicit val partitionId = PartitionId("store4s", None)

  "An EntityEncoder" should "encode case class into Entity" in {
    val ans = Entity(
      Some(Key(partitionId, Seq(PathElement("Zombie", None, Some("heroine"))))),
      Map(
        "number" -> Value(Some(false), integerValue = Some("1")),
        "name" -> Value(Some(false), stringValue = Some("Sakura Minamoto")),
        "girl" -> Value(Some(false), booleanValue = Some(true))
      )
    )
    case class Zombie(number: Int, name: String, girl: Boolean)
    val res = Zombie(1, "Sakura Minamoto", true).asEntity("heroine")
    assert(res == ans)
  }

  it should "support incomplete key" in {
    val ans = Entity(
      Some(Key(partitionId, Seq(PathElement("User", None, None)))),
      Map("name" -> Value(Some(false), stringValue = Some("John")))
    )
    case class User(name: String)
    val res = User("John").asEntity
    assert(res == ans)
  }

  it should "support nullable value" in {
    val ans = Entity(
      Some(Key(partitionId, Seq(PathElement("User", None, None)))),
      Map("name" -> Value(Some(false), nullValue = Some("NULL_VALUE")))
    )
    case class User(name: Option[String])
    val res = User(None).asEntity
    assert(res == ans)
  }

  it should "support array value" in {
    val ans = Entity(
      Some(Key(partitionId, Seq(PathElement("Group", None, None)))),
      Map(
        "id" -> Value(Some(false), integerValue = Some("1")),
        "members" -> Value(
          Some(false),
          arrayValue = Some(
            ArrayValue(
              Seq(
                Value(Some(false), stringValue = Some("A")),
                Value(Some(false), stringValue = Some("B")),
                Value(Some(false), stringValue = Some("C"))
              )
            )
          )
        )
      )
    )
    case class Group(id: Int, members: Seq[String])
    val res = Group(1, Seq("A", "B", "C")).asEntity
    assert(res == ans)
  }

  it should "support nested entity" in {
    val hometown = Entity(
      None,
      Map(
        "country" -> Value(Some(false), stringValue = Some("Japan")),
        "region" -> Value(Some(false), stringValue = Some("Kyushu")),
        "city" -> Value(Some(false), stringValue = Some("Saga"))
      )
    )
    val ans = Entity(
      Some(Key(partitionId, Seq(PathElement("Zombie", None, None)))),
      Map(
        "name" -> Value(Some(false), stringValue = Some("Sakura")),
        "hometown" -> Value(Some(false), entityValue = Some(hometown))
      )
    )
    // we need 3 fields in Hometown to check diverging implicit (Lazy)
    case class Hometown(country: String, region: String, city: String)
    case class Zombie(name: String, hometown: Hometown)
    val res = Zombie("Sakura", Hometown("Japan", "Kyushu", "Saga")).asEntity
    assert(res == ans)
  }

  it should "support excludeFromIndexes" in {
    val description =
      "A high school girl and aspiring idol who dies in 2008 after being hit by a truck following a life filled with misfortune."
    val ans = Entity(
      Some(Key(partitionId, Seq(PathElement("Zombie", None, Some("heroine"))))),
      Map(
        "number" -> Value(Some(false), integerValue = Some("1")),
        "name" -> Value(Some(false), stringValue = Some("Sakura Minamoto")),
        "description" -> Value(Some(true), stringValue = Some(description))
      )
    )
    case class Zombie(number: Int, name: String, description: String)
    implicit val encoder =
      EntityEncoder[Zombie].excludeFromIndexes(_.description)
    val res = Zombie(1, "Sakura Minamoto", description).asEntity("heroine")
    assert(res == ans)
  }

  it should "support excludeFromIndexes on array value" in {
    val ans = Entity(
      Some(Key(partitionId, Seq(PathElement("Group", None, None)))),
      Map(
        "id" -> Value(Some(false), integerValue = Some("1")),
        "members" -> Value(
          Some(true),
          arrayValue = Some(
            ArrayValue(
              Seq(
                Value(Some(true), stringValue = Some("A")),
                Value(Some(true), stringValue = Some("B")),
                Value(Some(true), stringValue = Some("C"))
              )
            )
          )
        )
      )
    )
    case class Group(id: Int, members: Seq[String])
    implicit val encoder = EntityEncoder[Group].excludeFromIndexes(_.members)
    val res = Group(1, Seq("A", "B", "C")).asEntity
    assert(res == ans)
  }

  it should "support ADT" in {
    val ans = Entity(
      Some(Key(partitionId, Seq(PathElement("Member", None, None)))),
      Map(
        "number" -> Value(Some(false), integerValue = Some("7")),
        "name" -> Value(Some(false), stringValue = Some("Maimai Yuzuriha")),
        "_type" -> Value(stringValue = Some("Human"))
      )
    )
    sealed trait Member
    case class Zombie(number: Int, name: String, died: String) extends Member
    case class Human(number: Int, name: String) extends Member
    val member: Member = Human(7, "Maimai Yuzuriha")
    val res = member.asEntity
    assert(res == ans)
  }

  "A ValueEncoder" should "support contramap" in {
    val ans = Entity(
      Some(Key(partitionId, Seq(PathElement("Zombie", None, None)))),
      Map(
        "name" -> Value(Some(false), stringValue = Some("Sakura Minamoto")),
        "birthday" -> Value(Some(false), stringValue = Some("1991-04-02"))
      )
    )
    implicit val enc = ValueEncoder.stringEncoder
      .contramap[LocalDate](_.toString())
    case class Zombie(name: String, birthday: LocalDate)
    val res = Zombie("Sakura Minamoto", LocalDate.of(1991, 4, 2)).asEntity
    assert(res == ans)
  }
}
