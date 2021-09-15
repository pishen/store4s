package store4s

import com.google.cloud.datastore.DatastoreException
import com.google.cloud.datastore.Entity
import com.google.cloud.datastore.FullEntity
import com.google.cloud.datastore.KeyFactory
import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec

import java.time.LocalDate

class DecoderSpec extends AnyFlatSpec with EitherValues {
  val keyFactory = new KeyFactory("store4s")

  "An EntityDecoder" should "decode Entity into case class" in {
    val zG = Entity
      .newBuilder(keyFactory.setKind("Zombie").newKey("heroine"))
      .set("number", 1)
      .set("name", "Sakura Minamoto")
      .set("girl", true)
      .build()

    case class Zombie(number: Int, name: String, girl: Boolean)
    val zS = Zombie(1, "Sakura Minamoto", true)

    assert(decodeEntity[Zombie](zG) == Right(zS))
  }

  it should "get a ClassCastException for unmatched type" in {
    val e = FullEntity
      .newBuilder()
      .set("id", "abc")
      .build()
    case class User(id: Int)

    assert(decodeEntity[User](e).left.value.isInstanceOf[ClassCastException])
  }

  it should "get a DatastoreException for not found property" in {
    val e = FullEntity
      .newBuilder()
      .set("id", 1)
      .build()
    case class User(name: String)

    assert(decodeEntity[User](e).left.value.isInstanceOf[DatastoreException])
  }

  it should "support nullable value" in {
    val userG = FullEntity.newBuilder().setNull("name").build()

    case class User(name: Option[String], age: Option[Int])
    val userS = User(None, None)

    assert(decodeEntity[User](userG) == Right(userS))
  }

  it should "support list value" in {
    val groupG = FullEntity
      .newBuilder()
      .set("id", 1)
      .set("members", "A", "B", "C")
      .build()

    case class Group(id: Int, members: Seq[String])
    val groupS = Group(1, Seq("A", "B", "C"))

    assert(decodeEntity[Group](groupG) == Right(groupS))
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
    val zS = Zombie("Sakura", Hometown("Japan", "Kyushu", "Saga"))

    assert(decodeEntity[Zombie](zG) == Right(zS))
  }

  "A ValueDecoder" should "support map" in {
    val zG = FullEntity
      .newBuilder(keyFactory.setKind("Zombie").newKey())
      .set("name", "Sakura Minamoto")
      .set("birthday", "1991-04-02")
      .build()

    implicit val dec = ValueDecoder.stringDecoder.map(LocalDate.parse)

    case class Zombie(name: String, birthday: LocalDate)
    val zS = Zombie("Sakura Minamoto", LocalDate.of(1991, 4, 2))

    assert(decodeEntity[Zombie](zG) == Right(zS))
  }
}
