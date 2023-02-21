package store4s.async.testing

import io.circe.Decoder
import io.circe.Encoder
import io.circe.Printer
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._
import org.scalatest.OneInstancePerTest
import org.scalatest.flatspec.AnyFlatSpec
import store4s.async.RespAs
import store4s.async._
import sttp.client3.IsOption
import sttp.client3.circe._

class BackendSimulatorSpec extends AnyFlatSpec with OneInstancePerTest {
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

  "A BackendSimulator" should "filter on kind" in {
    case class Group(name: String)
    case class User(group: String, name: String)
    val g = Group("A")
    val u1 = User("A", "user1")
    val u2 = User("A", "user2")
    ds.insert(g.asEntity("A"))
    ds.insert(u1.asEntity("user1"), u2.asEntity("user2"))
    val res = ds.runQuery(Query.from[User])
    assert(res.toSeq.toSet == Set(u1, u2))
  }

  it should "filter on multiple properties" in {
    case class User(id: Int, name: String, age: Int)
    val u0 = User(0, "User0", 10)
    val u1 = User(1, "User1", 11)
    val u2 = User(2, "User2", 12)
    ds.insert(u0.asEntity(1L), u1.asEntity(2L), u2.asEntity(3L))
    val res = ds.runQuery(
      Query
        .from[User]
        .filter(u => u.id == 1 && u.name == "User1" && u.age == 11)
    )
    assert(res.toSeq.head == u1)
  }

  it should "support inequality filter" in {
    case class User(id: Int, name: String, age: Int)
    val u0 = User(0, "User0", 10)
    val u1 = User(1, "User1", 11)
    val u2 = User(2, "User2", 12)
    ds.insert(u0.asEntity(1L), u1.asEntity(2L), u2.asEntity(3L))
    val res = ds.runQuery(Query.from[User].filter(_.age >= 11))
    assert(res.toSeq.toSet == Set(u1, u2))
  }

  it should "order by multiple properties" in {
    case class User(group: Int, name: String, age: Int)
    val u0 = User(1, "User0", 10)
    val u1 = User(0, "User1", 11)
    val u2 = User(1, "User2", 12)
    ds.insert(u0.asEntity(1L), u1.asEntity(2L), u2.asEntity(3L))
    val res = ds.runQuery(
      Query.from[User].sortBy(_.group.asc, _.age.desc)
    )
    assert(res.toSeq == Seq(u1, u2, u0))
  }
}
