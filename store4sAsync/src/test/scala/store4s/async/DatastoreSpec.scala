package store4s.async

import io.circe.Decoder
import io.circe.Encoder
import io.circe.Printer
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._
import org.scalatest.OneInstancePerTest
import org.scalatest.flatspec.AnyFlatSpec
import store4s.async.testing.BackendSimulator
import store4s.async.testing.BodyDecoder
import store4s.async.testing.BodyEncoder
import sttp.client3.IsOption
import sttp.client3._
import sttp.client3.circe._

class DatastoreSpec extends AnyFlatSpec with OneInstancePerTest {
  implicit val printerDrop = Printer.noSpaces.copy(dropNullValues = true)
  implicit def respAs[B: Decoder: IsOption] = RespAs.create(asJson[B])

  implicit def decoder[T: Decoder] =
    BodyDecoder.create(s => decode[T](s).toTry.get)
  implicit def encoder[T: Encoder] =
    BodyEncoder.create[T](_.asJson.noSpaces)
  val simulator = BackendSimulator("store4s")
  implicit val ds = Datastore(
    () => AccessToken("token", Long.MaxValue),
    "store4s",
    simulator.backend
  )

  "A Datastore" should "support allocateIds" in {
    case class User(name: String)
    val ids = ds.allocateIds[User](3)
    assert(simulator.allocatedKeys.forall(_.path.head.kind == "User"))
    assert(simulator.allocatedKeys.map(_.path.head.id.get.toLong) == ids.toSet)
  }

  it should "support insert" in {
    case class Zombie(name: String)
    val z = Zombie("Sakura Minamoto")
    ds.insert(z.asEntity("heroine"))
    assert(ds.lookupByName[Zombie]("heroine").get == z)
    val z2 = Zombie("Maimai Yuzuriha")
    assertThrows[HttpError[_]](ds.insert(z2.asEntity("heroine")))
  }

  it should "support upsert" in {
    case class Zombie(name: String)
    val z = Zombie("Sakura Minamoto")
    ds.upsert(z.asEntity("heroine"))
    assert(ds.lookupByName[Zombie]("heroine").get == z)
    val z2 = Zombie("Maimai Yuzuriha")
    ds.upsert(z2.asEntity("heroine"))
    assert(ds.lookupByName[Zombie]("heroine").get == z2)
  }

  it should "support update" in {
    case class Zombie(name: String)
    val z = Zombie("Sakura Minamoto")
    assertThrows[HttpError[_]](ds.update(z.asEntity("heroine")))
    val z2 = Zombie("Maimai Yuzuriha")
    ds.insert(z.asEntity("heroine"))
    ds.update(z2.asEntity("heroine"))
    assert(ds.lookupByName[Zombie]("heroine").get == z2)
  }

  it should "support deleteById" in {
    case class Zombie(name: String)
    val z = Zombie("Sakura Minamoto")
    ds.insert(z.asEntity(1L))
    ds.deleteById[Zombie](1L)
    assert(ds.lookupById[Zombie](1L).isEmpty)
  }

  it should "support deleteByName" in {
    case class Zombie(name: String)
    val z = Zombie("Sakura Minamoto")
    ds.insert(z.asEntity("heroine"))
    ds.deleteByName[Zombie]("heroine")
    assert(ds.lookupByName[Zombie]("heroine").isEmpty)
  }

  it should "support lookupById" in {
    case class Zombie(name: String)
    val z = Zombie("Sakura Minamoto")
    ds.insert(z.asEntity(1L))
    assert(ds.lookupById[Zombie](1L).get == z)
  }

  it should "support lookupByName" in {
    case class Zombie(name: String)
    val z = Zombie("Sakura Minamoto")
    ds.insert(z.asEntity("heroine"))
    assert(ds.lookupByName[Zombie]("heroine").get == z)
  }

  it should "support runQuery" in {
    case class Zombie(name: String)
    val z = Zombie("Sakura Minamoto")
    ds.insert(z.asEntity("heroine"))
    val res = ds.runQuery(
      Query.from[Zombie].filter(_.name == "Sakura Minamoto")
    )
    assert(res.toSeq.head == Zombie("Sakura Minamoto"))
  }

  it should "support transaction" in {
    case class Zombie(name: String)
    val e = Zombie("Sakura Minamoto").asEntity("heroine")
    ds.transaction { tx =>
      assert(simulator.transactions.head == tx.id)
      ("Done", Seq(tx.insert(e)))
    }
    assert(simulator.transactions.isEmpty)
    assert(simulator.db.values.head == e)
  }

  it should "rollback automatically when transaction failed" in {
    val caught = intercept[RuntimeException] {
      ds.transaction { tx =>
        assert(simulator.transactions.head == tx.id)
        sys.error("Transaction failed")
      }
    }
    assert(caught.getMessage == "Transaction failed")
    assert(simulator.transactions.isEmpty)
  }
}
