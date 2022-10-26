package store4s.async

import com.google.auth.oauth2.AccessToken
import com.google.auth.oauth2.GoogleCredentials
import io.circe.Decoder
import io.circe.Printer
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._
import org.scalamock.scalatest.MockFactory
import org.scalatest.flatspec.AnyFlatSpec
import sttp.client3.IsOption
import sttp.client3.Response
import sttp.client3.StringBody
import sttp.client3.circe._
import sttp.client3.testing.SttpBackendStub
import sttp.model.Method

import java.util.Date

import model._

class DatastoreSpec extends AnyFlatSpec with MockFactory {
  val credentials = new GoogleCredentials(
    new AccessToken("token_value", new Date(Long.MaxValue))
  )
  implicit val printerDrop = Printer.noSpaces.copy(dropNullValues = true)
  implicit def respAs[B: Decoder: IsOption] = RespAs.create(asJson[B])

  "Datastore" should "allocateId" in {
    case class User(name: String)
    val backend = SttpBackendStub.synchronous
      .whenRequestMatchesPartial {
        case r if r.uri.path.last == "store4s:allocateIds" =>
          assert(r.method == Method.POST)
          val in =
            decode[AllocateIdBody](r.body.asInstanceOf[StringBody].s).toTry.get
          val ans = AllocateIdBody(
            Seq(
              Key(
                PartitionId("store4s", None),
                Seq(PathElement("User", None, None))
              )
            )
          )
          assert(in == ans)
          val out = AllocateIdBody(
            Seq(
              Key(
                PartitionId("store4s", None),
                Seq(PathElement("User", Some("123"), None))
              )
            )
          )
          Response.ok(out.asJson.noSpaces)
      }

    val ds = Datastore("store4s", None, credentials, backend)
    assert(ds.allocateId[User] == 123)
  }
}
