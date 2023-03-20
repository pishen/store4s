package store4s.sttp

import io.circe.Decoder
import io.circe.Printer
import io.circe.generic.auto._
import org.scalatest.flatspec.AnyFlatSpec
import sttp.client3.IsOption
import sttp.client3.circe._

class DatastoreStubSpec extends AnyFlatSpec {
  implicit val printerDrop = Printer.noSpaces.copy(dropNullValues = true)
  implicit def respAs[B: Decoder: IsOption] = RespAs.create(asJson[B])

  "A DatastoreStub" should "support insert" in {
    implicit val ds = DatastoreStub.synchronous("store4s")
    case class Zombie(name: String)
    ds.insert(Zombie("Sakura Minamoto").asEntity("heroine"))
    assert(ds.lookupByName[Zombie]("heroine").get == Zombie("Sakura Minamoto"))
  }
}
