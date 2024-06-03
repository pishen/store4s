package store4s.sttp

import io.circe.Decoder
import io.circe.Printer
import io.circe.generic.auto._
import org.scalatest.flatspec.AnyFlatSpec
import sttp.client3.IsOption
import sttp.client3.circe._

class DatastoreEmulatorSpec extends AnyFlatSpec {
  implicit val printerDrop: Printer =
    Printer.noSpaces.copy(dropNullValues = true)
  implicit def deserializer[B: Decoder: IsOption]: BodyDeserializer[B] =
    BodyDeserializer.from(asJson[B])

  "A DatastoreEmulator" should "support insert" in {
    implicit val ds = DatastoreEmulator.synchronous("store4s")
    case class Zombie(name: String)
    ds.insert(Zombie("Sakura Minamoto").asEntity("heroine"))
    assert(ds.lookupByName[Zombie]("heroine").get == Zombie("Sakura Minamoto"))
  }

  it should "support array exists" in {
    implicit val ds = DatastoreEmulator.synchronous("store4s")
    case class Group(members: Seq[String])
    val group = Group(Seq("Sakura Minamoto", "Ai Mizuno"))
    ds.insert(group.asEntity("Franchouchou"))
    val res = Query
      .from[Group]
      .filter(_.members.exists(_ == "Sakura Minamoto"))
      .run(ds)
      .toSeq
    assert(res == Seq(group))
  }

  it should "support null value" in {
    implicit val ds = DatastoreEmulator.synchronous("store4s")
    case class Zombie(name: String, birthday: Option[String])
    val z = Zombie("Tae Yamada", None)
    ds.insert(z.asEntity("zero"))
    assert(ds.lookupByName[Zombie]("zero").get == z)
  }
}
