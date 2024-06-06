package store4s.rpc

import com.google.datastore.v1.entity.Entity
import com.google.datastore.v1.entity.Value
import com.google.protobuf.struct.NullValue
import org.scalatest.flatspec.AnyFlatSpec
import com.google.datastore.v1.entity.ArrayValue
import java.time.LocalDate

class DecoderSpec extends AnyFlatSpec {
  "Decoder" should "decode Entity into case class" in {
    case class Zombie(number: Int, name: String, girl: Boolean)
    val ans = Zombie(1, "Sakura Minamoto", true)
    val entity = Entity().addProperties(
      "number" -> Value().withIntegerValue(1),
      "name" -> Value().withStringValue("Sakura Minamoto"),
      "girl" -> Value().withBooleanValue(true)
    )
    assert(entity.as[Zombie] == ans)
  }

  it should "get NoSuchElementException for unmatched type" in {
    val entity = Entity().addProperties("id" -> Value().withStringValue("abc"))
    case class User(id: Int)
    val error = intercept[NoSuchElementException] {
      entity.as[User]
    }
    assert(error.getMessage == "None.get")
  }

  it should "get NoSuchElementException for not found property" in {
    val entity = Entity().addProperties("id" -> Value().withIntegerValue(1))
    case class User(name: String)
    val error = intercept[NoSuchElementException] {
      entity.as[User]
    }
    assert(error.getMessage == "key not found: name")
  }

  it should "support nullable value" in {
    case class User(name: Option[String], age: Option[Int])
    val ans = User(None, None)
    val entity = Entity().addProperties(
      "name" -> Value().withNullValue(NullValue.NULL_VALUE)
    )
    assert(entity.as[User] == ans)
  }

  it should "support list value" in {
    case class Group(id: Int, members: Seq[String])
    val ans = Group(1, Seq("A", "B", "C"))
    val entity = Entity().addProperties(
      "id" -> Value().withIntegerValue(1),
      "members" -> Value().withArrayValue(
        ArrayValue().addValues(
          Value().withStringValue("A"),
          Value().withStringValue("B"),
          Value().withStringValue("C")
        )
      )
    )
    assert(entity.as[Group] == ans)
  }

  it should "support empty list" in {
    case class Group(id: Int, members: Seq[String])
    val ans = Group(1, Seq())
    val entity = Entity().addProperties(
      "id" -> Value().withIntegerValue(1),
      "members" -> Value().withArrayValue(ArrayValue())
    )
    assert(entity.as[Group] == ans)
  }

  it should "support nested entity" in {
    case class Hometown(country: String, region: String, city: String)
    case class Zombie(name: String, hometown: Hometown)
    val ans = Zombie("Sakura", Hometown("Japan", "Kyushu", "Saga"))
    val hometown = Entity().addProperties(
      "country" -> Value().withStringValue("Japan"),
      "region" -> Value().withStringValue("Kyushu"),
      "city" -> Value().withStringValue("Saga")
    )
    val entity = Entity().addProperties(
      "name" -> Value().withStringValue("Sakura"),
      "hometown" -> Value().withEntityValue(hometown)
    )
    assert(entity.as[Zombie] == ans)
  }

  it should "support map" in {
    case class Zombie(name: String, birthday: LocalDate)
    val ans = Zombie("Sakura Minamoto", LocalDate.of(1991, 4, 2))
    implicit val dec: Decoder[LocalDate] = Decoder.string.map(LocalDate.parse)
    val entity = Entity().addProperties(
      "name" -> Value().withStringValue("Sakura Minamoto"),
      "birthday" -> Value().withStringValue("1991-04-02")
    )
    assert(entity.as[Zombie] == ans)
  }
}
