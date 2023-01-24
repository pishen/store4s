package store4s.async

import com.google.auth.oauth2.AccessToken
import com.google.auth.oauth2.GoogleCredentials
import io.circe.Decoder
import io.circe.Encoder
import io.circe.Printer
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._
import org.scalamock.scalatest.MockFactory
import org.scalatest.flatspec.AnyFlatSpec
import store4s.async.model.AllocateIdBody
import store4s.async.model.CommitRequest
import store4s.async.model.CommitResponse
import store4s.async.model.Entity
import store4s.async.model.EntityResult
import store4s.async.model.Filter
import store4s.async.model.Key
import store4s.async.model.KindExpression
import store4s.async.model.LookupRequest
import store4s.async.model.LookupResponse
import store4s.async.model.Mutation
import store4s.async.model.MutationResult
import store4s.async.model.PartitionId
import store4s.async.model.PathElement
import store4s.async.model.PropertyFilter
import store4s.async.model.PropertyReference
import store4s.async.model.QueryResultBatch
import store4s.async.model.ReadOptions
import store4s.async.model.RunQueryRequest
import store4s.async.model.RunQueryResponse
import store4s.async.model.Value
import sttp.client3.IsOption
import sttp.client3.Response
import sttp.client3.StringBody
import sttp.client3.circe._
import sttp.client3.testing.SttpBackendStub
import sttp.model.Method

import java.util.Date

class DatastoreSpec extends AnyFlatSpec with MockFactory {
  val credentials = new GoogleCredentials(
    new AccessToken("token_value", new Date(Long.MaxValue))
  )
  implicit val partitionId = PartitionId("store4s", None)
  implicit val printerDrop = Printer.noSpaces.copy(dropNullValues = true)
  implicit def respAs[B: Decoder: IsOption] = RespAs.create(asJson[B])

  def stubBackend[I: Decoder, O: Encoder](
      pathLast: String,
      expectedInput: I,
      output: O
  ) = SttpBackendStub.synchronous.whenRequestMatchesPartial {
    case r if r.uri.path.last == pathLast =>
      assert(r.method == Method.POST)
      val in = decode[I](r.body.asInstanceOf[StringBody].s).toTry.get
      assert(in == expectedInput)
      Response.ok(output.asJson.noSpaces)
  }

  "A Datastore" should "support allocateIds" in {
    case class User(name: String)
    val backend = stubBackend(
      "store4s:allocateIds",
      AllocateIdBody(
        Seq(
          Key(
            PartitionId("store4s", None),
            Seq(PathElement("User", None, None))
          )
        )
      ),
      AllocateIdBody(
        Seq(
          Key(
            PartitionId("store4s", None),
            Seq(PathElement("User", Some("123"), None))
          )
        )
      )
    )
    val ans = Seq(
      Entity(
        Some(Key(partitionId, Seq(PathElement("User", Some("123"), None)))),
        Map("name" -> Value(Some(false), stringValue = Some("John")))
      )
    )

    val ds = Datastore(credentials, backend)
    assert(ds.allocateIds(User("John")) == ans)
  }

  it should "support insert" in {
    case class Zombie(name: String)
    val entity = Zombie("Sakura Minamoto").asEntity("heroine")
    val backend = stubBackend(
      "store4s:commit",
      CommitRequest(
        "NON_TRANSACTIONAL",
        Seq(Mutation(insert = Some(entity))),
        None
      ),
      CommitResponse(Seq(MutationResult(None, "1671938766838115")), 3)
    )

    val ds = Datastore(credentials, backend)
    assert(ds.insert(entity).nonEmpty)
  }

  it should "support upsert" in {
    case class Zombie(name: String)
    val entity = Zombie("Sakura Minamoto").asEntity("heroine")
    val backend = stubBackend(
      "store4s:commit",
      CommitRequest(
        "NON_TRANSACTIONAL",
        Seq(Mutation(upsert = Some(entity))),
        None
      ),
      CommitResponse(Seq(MutationResult(None, "1671939685199045")), 3)
    )

    val ds = Datastore(credentials, backend)
    assert(ds.upsert(entity).nonEmpty)
  }

  it should "support update" in {
    case class Zombie(name: String)
    val entity = Zombie("Sakura Minamoto").asEntity("heroine")
    val backend = stubBackend(
      "store4s:commit",
      CommitRequest(
        "NON_TRANSACTIONAL",
        Seq(Mutation(update = Some(entity))),
        None
      ),
      CommitResponse(Seq(MutationResult(None, "1671939685199045")), 3)
    )

    val ds = Datastore(credentials, backend)
    assert(ds.update(entity).nonEmpty)
  }

  it should "support deleteById" in {
    case class Zombie(name: String)
    val backend = stubBackend(
      "store4s:commit",
      CommitRequest(
        "NON_TRANSACTIONAL",
        Seq(
          Mutation(delete =
            Some(
              Key(partitionId, Seq(PathElement("Zombie", Some("123"), None)))
            )
          )
        ),
        None
      ),
      CommitResponse(Seq(MutationResult(None, "1671940358286124")), 3)
    )

    val ds = Datastore(credentials, backend)
    assert(ds.deleteById[Zombie](123L).nonEmpty)
  }

  it should "support deleteByName" in {
    case class Zombie(name: String)
    val backend = stubBackend(
      "store4s:commit",
      CommitRequest(
        "NON_TRANSACTIONAL",
        Seq(
          Mutation(delete =
            Some(
              Key(
                partitionId,
                Seq(PathElement("Zombie", None, Some("heroine")))
              )
            )
          )
        ),
        None
      ),
      CommitResponse(Seq(MutationResult(None, "1671940358286124")), 3)
    )

    val ds = Datastore(credentials, backend)
    assert(ds.deleteByName[Zombie]("heroine").nonEmpty)
  }

  it should "support lookupById" in {
    case class Zombie(name: String)
    val backend = stubBackend(
      "store4s:lookup",
      LookupRequest(
        ReadOptions(Some("STRONG"), None),
        Seq(Key(partitionId, Seq(PathElement("Zombie", Some("123"), None))))
      ),
      LookupResponse(
        Some(Seq(EntityResult(Zombie("Sakura Minamoto").asEntity(123L), None))),
        None,
        None
      )
    )

    val ds = Datastore(credentials, backend)
    assert(ds.lookupById[Zombie](123L).head == Zombie("Sakura Minamoto"))
  }

  it should "support lookupByName" in {
    case class Zombie(name: String)
    val backend = stubBackend(
      "store4s:lookup",
      LookupRequest(
        ReadOptions(Some("STRONG"), None),
        Seq(Key(partitionId, Seq(PathElement("Zombie", None, Some("heroine")))))
      ),
      LookupResponse(
        Some(
          Seq(EntityResult(Zombie("Sakura Minamoto").asEntity("heroine"), None))
        ),
        None,
        None
      )
    )

    val ds = Datastore(credentials, backend)
    assert(ds.lookupByName[Zombie]("heroine").head == Zombie("Sakura Minamoto"))
  }

  it should "support runQuery" in {
    case class Zombie(name: String)
    val backend = stubBackend(
      "store4s:runQuery",
      RunQueryRequest(
        partitionId,
        ReadOptions(Some("STRONG"), None),
        model.Query(
          Seq(KindExpression("Zombie")),
          filter = Some(
            Filter(
              propertyFilter = Some(
                PropertyFilter(
                  PropertyReference("name"),
                  "EQUAL",
                  Value(Some(false), stringValue = Some("Sakura Minamoto"))
                )
              )
            )
          )
        )
      ),
      RunQueryResponse(
        QueryResultBatch(
          None,
          None,
          "FULL",
          Some(
            Seq(
              EntityResult(
                Zombie("Sakura Minamoto").asEntity("heroine"),
                Some(
                  "Ci4SKGoRYn5hcHAtaWRuLXdlYi1kZXZyEwsSBlpvbWJpZRiAgICYjPCBCgwYACAA"
                )
              )
            )
          ),
          "Ci4SKGoRYn5hcHAtaWRuLXdlYi1kZXZyEwsSBlpvbWJpZRiAgICYjPCBCgwYACAA",
          "NO_MORE_RESULTS"
        )
      )
    )

    val ds = Datastore(credentials, backend)
    val res =
      ds.runQuery(Query.from[Zombie].filter(_.name == "Sakura Minamoto"))
    assert(res.toSeq.head == Zombie("Sakura Minamoto"))
    assert(
      res.endCursor == "Ci4SKGoRYn5hcHAtaWRuLXdlYi1kZXZyEwsSBlpvbWJpZRiAgICYjPCBCgwYACAA"
    )
  }
}
