package store4s.sttp

import io.circe.Decoder
import io.circe.Printer
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._
import org.scalatest.flatspec.AnyFlatSpec
import store4s.sttp.model.{Query => _, _}
import sttp.client3.IsOption
import sttp.client3._
import sttp.client3.circe._
import sttp.client3.testing.RecordingSttpBackend
import sttp.client3.testing.SttpBackendStub
import sttp.model.Method

import scala.util.Random

class DatastoreSpec extends AnyFlatSpec {
  implicit val printerDrop = Printer.noSpaces.copy(dropNullValues = true)
  implicit def deserializer[B: Decoder: IsOption] =
    BodyDeserializer.from(asJson[B])

  def buildDS[F[_], P](backend: SttpBackend[F, P]) = Datastore(
    new AccessToken { def get() = "token" },
    "store4s",
    backend
  )
  def decodeBody[T: Decoder](body: RequestBody[_]) =
    decode[T](body.asInstanceOf[StringBody].s).toTry.get

  "A Datastore" should "support allocateIds" in {
    val randomIds = Seq.fill(3)(Random.nextLong())
    val backend = new RecordingSttpBackend(
      SttpBackendStub.synchronous
        .whenRequestMatches(_.uri.path.last == "store4s:allocateIds")
        .thenRespond(
          AllocateIdBody(
            randomIds.map(id =>
              Key(
                PartitionId("store4s", None),
                Seq(PathElement("User", Some(id.toString), None))
              )
            )
          ).asJson.noSpaces
        )
    )
    val ds = buildDS(backend)

    case class User(name: String)
    val ids = ds.allocateIds[User](3)

    val req = backend.allInteractions.head._1
    assert(req.method == Method.POST)
    val reqBody = decodeBody[AllocateIdBody](req.body)
    assert(
      reqBody == AllocateIdBody(
        Seq.fill(3)(
          Key(
            PartitionId("store4s", None),
            Seq(PathElement("User", None, None))
          )
        )
      )
    )
    assert(ids == randomIds)
  }

  def commitBackend() = new RecordingSttpBackend(
    SttpBackendStub.synchronous
      .whenRequestMatches(_.uri.path.last == "store4s:commit")
      .thenRespond(
        CommitResponse(Some(Seq(MutationResult(None, "1")))).asJson.noSpaces
      )
  )

  it should "support insert" in {
    val backend = commitBackend()
    implicit val ds = buildDS(backend)

    case class Zombie(name: String)
    val e = Zombie("Sakura Minamoto").asEntity("heroine")
    val res = ds.insert(e)

    val req = backend.allInteractions.head._1
    assert(req.method == Method.POST)
    val reqBody = decodeBody[CommitRequest](req.body)
    assert(
      reqBody == CommitRequest(
        "NON_TRANSACTIONAL",
        Seq(Mutation(insert = Some(e))),
        None
      )
    )
    assert(res.head == None)
  }

  it should "support upsert" in {
    val backend = commitBackend()
    implicit val ds = buildDS(backend)

    case class Zombie(name: String)
    val e = Zombie("Sakura Minamoto").asEntity("heroine")
    val res = ds.upsert(e)

    val req = backend.allInteractions.head._1
    assert(req.method == Method.POST)
    val reqBody = decodeBody[CommitRequest](req.body)
    assert(
      reqBody == CommitRequest(
        "NON_TRANSACTIONAL",
        Seq(Mutation(upsert = Some(e))),
        None
      )
    )
    assert(res.head == None)
  }

  it should "support update" in {
    val backend = commitBackend()
    implicit val ds = buildDS(backend)

    case class Zombie(name: String)
    val e = Zombie("Sakura Minamoto").asEntity("heroine")
    val res = ds.update(e)

    val req = backend.allInteractions.head._1
    assert(req.method == Method.POST)
    val reqBody = decodeBody[CommitRequest](req.body)
    assert(
      reqBody == CommitRequest(
        "NON_TRANSACTIONAL",
        Seq(Mutation(update = Some(e))),
        None
      )
    )
    assert(res.head == None)
  }

  it should "support deleteById" in {
    val backend = commitBackend()
    implicit val ds = buildDS(backend)

    case class Zombie(name: String)
    val res = ds.deleteById[Zombie](123L)

    val req = backend.allInteractions.head._1
    assert(req.method == Method.POST)
    val reqBody = decodeBody[CommitRequest](req.body)
    assert(
      reqBody == CommitRequest(
        "NON_TRANSACTIONAL",
        Seq(
          Mutation(delete =
            Some(
              Key(
                PartitionId("store4s", None),
                Seq(PathElement("Zombie", Some("123"), None))
              )
            )
          )
        ),
        None
      )
    )
    assert(res.head == None)
  }

  it should "support deleteByName" in {
    val backend = commitBackend()
    implicit val ds = buildDS(backend)

    case class Zombie(name: String)
    val res = ds.deleteByName[Zombie]("heroine")

    val req = backend.allInteractions.head._1
    assert(req.method == Method.POST)
    val reqBody = decodeBody[CommitRequest](req.body)
    assert(
      reqBody == CommitRequest(
        "NON_TRANSACTIONAL",
        Seq(
          Mutation(delete =
            Some(
              Key(
                PartitionId("store4s", None),
                Seq(PathElement("Zombie", None, Some("heroine")))
              )
            )
          )
        ),
        None
      )
    )
    assert(res.head == None)
  }

  it should "support lookupById" in {
    val key = Key(
      PartitionId("store4s", None),
      Seq(PathElement("Zombie", Some("123"), None))
    )
    val e = Entity(
      Some(key),
      Map("name" -> Value(stringValue = Some("Sakura Minamoto")))
    )
    val backend = new RecordingSttpBackend(
      SttpBackendStub.synchronous
        .whenRequestMatches(_.uri.path.last == "store4s:lookup")
        .thenRespond(
          LookupResponse(Some(Seq(EntityResult(e, None))), None).asJson.noSpaces
        )
    )
    implicit val ds = buildDS(backend)

    case class Zombie(name: String)
    val res = ds.lookupById[Zombie](123L)

    val req = backend.allInteractions.head._1
    assert(req.method == Method.POST)
    val reqBody = decodeBody[LookupRequest](req.body)
    assert(
      reqBody == LookupRequest(ReadOptions(Some("STRONG"), None), Seq(key))
    )
    assert(res.head == Zombie("Sakura Minamoto"))
  }

  it should "support lookupByName" in {
    val key = Key(
      PartitionId("store4s", None),
      Seq(PathElement("Zombie", None, Some("heroine")))
    )
    val e = Entity(
      Some(key),
      Map("name" -> Value(stringValue = Some("Sakura Minamoto")))
    )
    val backend = new RecordingSttpBackend(
      SttpBackendStub.synchronous
        .whenRequestMatches(_.uri.path.last == "store4s:lookup")
        .thenRespond(
          LookupResponse(Some(Seq(EntityResult(e, None))), None).asJson.noSpaces
        )
    )
    implicit val ds = buildDS(backend)

    case class Zombie(name: String)
    val res = ds.lookupByName[Zombie]("heroine")

    val req = backend.allInteractions.head._1
    assert(req.method == Method.POST)
    val reqBody = decodeBody[LookupRequest](req.body)
    assert(
      reqBody == LookupRequest(ReadOptions(Some("STRONG"), None), Seq(key))
    )
    assert(res.head == Zombie("Sakura Minamoto"))
  }

  it should "support runQuery" in {
    val e = Entity(
      Some(
        Key(
          PartitionId("store4s", None),
          Seq(PathElement("Zombie", None, Some("heroine")))
        )
      ),
      Map("name" -> Value(stringValue = Some("Sakura Minamoto")))
    )
    val backend = new RecordingSttpBackend(
      SttpBackendStub.synchronous
        .whenRequestMatches(_.uri.path.last == "store4s:runQuery")
        .thenRespond(
          RunQueryResponse(
            QueryResultBatch(
              None,
              None,
              "FULL",
              Some(Seq(EntityResult(e, None))),
              "xyz",
              "NO_MORE_RESULTS"
            )
          ).asJson.noSpaces
        )
    )
    implicit val ds = buildDS(backend)

    case class Zombie(name: String)
    val res = Query.from[Zombie].filter(_.name == "Sakura Minamoto").run(ds)

    val req = backend.allInteractions.head._1
    assert(req.method == Method.POST)
    val reqBody = decodeBody[RunQueryRequest](req.body)
    assert(
      reqBody == RunQueryRequest(
        PartitionId("store4s", None),
        ReadOptions(Some("STRONG"), None),
        store4s.sttp.model.Query(
          Seq(KindExpression("Zombie")),
          Some(
            Filter(
              None,
              Some(
                PropertyFilter(
                  PropertyReference("name"),
                  "EQUAL",
                  Value(Some(false), stringValue = Some("Sakura Minamoto"))
                )
              )
            )
          )
        )
      )
    )
    assert(res.toSeq.head == Zombie("Sakura Minamoto"))
    assert(res.endCursor == "xyz")
  }

  it should "support runQuery with NOT_FINISHED" in {
    val responses = Seq(
      ("Sakura Minamoto", "NOT_FINISHED"),
      ("Saki Nikaido", "NO_MORE_RESULTS")
    ).map { case (name, moreResults) =>
      val e = Entity(
        Some(
          Key(
            PartitionId("store4s", None),
            Seq(PathElement("Zombie", None, Some(name)))
          )
        ),
        Map("name" -> Value(stringValue = Some(name)))
      )
      RunQueryResponse(
        QueryResultBatch(
          None,
          None,
          "FULL",
          Some(Seq(EntityResult(e, None))),
          s"cursor_${name}",
          moreResults
        )
      ).asJson.noSpaces
    }
    val backend = new RecordingSttpBackend(
      SttpBackendStub.synchronous
        .whenRequestMatches(_.uri.path.last == "store4s:runQuery")
        .thenRespondCyclic(responses: _*)
    )
    implicit val ds = buildDS(backend)

    case class Zombie(name: String)
    val res = Query.from[Zombie].run(ds)

    val reqs = backend.allInteractions.map(_._1)
    assert(reqs.size == 2)
    assert(
      decodeBody[RunQueryRequest](reqs(1).body).query.startCursor ==
        Some("cursor_Sakura Minamoto")
    )
    assert(res.toSeq == Seq(Zombie("Sakura Minamoto"), Zombie("Saki Nikaido")))
    assert(res.endCursor == "cursor_Saki Nikaido")
  }

  it should "support transaction" in {
    val key = Key(
      PartitionId("store4s", None),
      Seq(PathElement("Zombie", Some("123"), None))
    )
    val e = Entity(
      Some(key),
      Map("name" -> Value(stringValue = Some("Sakura Minamoto")))
    )
    val backend = new RecordingSttpBackend(
      SttpBackendStub.synchronous
        .whenRequestMatches(_.uri.path.last == "store4s:beginTransaction")
        .thenRespond(BeginTransactionResponse("magic-tx").asJson.noSpaces)
        .whenRequestMatches(_.uri.path.last == "store4s:lookup")
        .thenRespond(
          LookupResponse(Some(Seq(EntityResult(e, None))), None).asJson.noSpaces
        )
        .whenRequestMatches(_.uri.path.last == "store4s:commit")
        .thenRespond(
          CommitResponse(Some(Seq(MutationResult(None, "1")))).asJson.noSpaces
        )
    )
    implicit val ds = buildDS(backend)

    case class Zombie(name: String)
    val res = ds.transaction { tx =>
      val z = tx.lookupById[Zombie](123L).get
      val e = z.copy(name = "ABC").asEntity(123L)
      (z, Seq(tx.update(e)))
    }

    val reqs = backend.allInteractions.map(_._1)
    assert(reqs.size == 3)
    assert(reqs.forall(_.method == Method.POST))
    assert(
      decodeBody[BeginTransactionRequest](reqs(0).body) ==
        BeginTransactionRequest(TransactionOptions(Some(ReadWrite(None)), None))
    )
    assert(
      decodeBody[LookupRequest](reqs(1).body) == LookupRequest(
        ReadOptions(None, Some("magic-tx")),
        Seq(key)
      )
    )
    assert(
      decodeBody[CommitRequest](reqs(2).body) == CommitRequest(
        "TRANSACTIONAL",
        Seq(Mutation(update = Some(Zombie("ABC").asEntity(123L)))),
        Some("magic-tx")
      )
    )
    assert(res == Zombie("Sakura Minamoto"))
  }

  it should "rollback automatically when transaction failed" in {
    val backend = new RecordingSttpBackend(
      SttpBackendStub.synchronous
        .whenRequestMatches(_.uri.path.last == "store4s:beginTransaction")
        .thenRespond(BeginTransactionResponse("magic-tx").asJson.noSpaces)
        .whenRequestMatches(_.uri.path.last == "store4s:rollback")
        .thenRespond("")
    )
    implicit val ds = buildDS(backend)

    val caught = intercept[RuntimeException] {
      ds.transaction { _ =>
        sys.error("Transaction failed")
      }
    }

    val reqs = backend.allInteractions.map(_._1)
    assert(reqs.size == 2)
    assert(reqs.forall(_.method == Method.POST))
    assert(
      decodeBody[BeginTransactionRequest](reqs(0).body) ==
        BeginTransactionRequest(TransactionOptions(Some(ReadWrite(None)), None))
    )
    assert(
      decodeBody[RollbackRequest](reqs(1).body) == RollbackRequest("magic-tx")
    )
    assert(caught.getMessage == "Transaction failed")
  }

  it should "support transactionReadOnly" in {
    val key = Key(
      PartitionId("store4s", None),
      Seq(PathElement("Zombie", Some("123"), None))
    )
    val e = Entity(
      Some(key),
      Map("name" -> Value(stringValue = Some("Sakura Minamoto")))
    )
    val backend = new RecordingSttpBackend(
      SttpBackendStub.synchronous
        .whenRequestMatches(_.uri.path.last == "store4s:beginTransaction")
        .thenRespond(BeginTransactionResponse("magic-tx").asJson.noSpaces)
        .whenRequestMatches(_.uri.path.last == "store4s:lookup")
        .thenRespond(
          LookupResponse(Some(Seq(EntityResult(e, None))), None).asJson.noSpaces
        )
        .whenRequestMatches(_.uri.path.last == "store4s:commit")
        .thenRespond(CommitResponse(None).asJson.noSpaces)
    )
    implicit val ds = buildDS(backend)

    case class Zombie(name: String)
    val res = ds.transactionReadOnly { tx =>
      tx.lookupById[Zombie](123L).get
    }

    val reqs = backend.allInteractions.map(_._1)
    assert(reqs.size == 3)
    assert(reqs.forall(_.method == Method.POST))
    assert(
      decodeBody[BeginTransactionRequest](reqs(0).body) ==
        BeginTransactionRequest(TransactionOptions(None, Some(ReadOnly())))
    )
    assert(
      decodeBody[LookupRequest](reqs(1).body) == LookupRequest(
        ReadOptions(None, Some("magic-tx")),
        Seq(key)
      )
    )
    assert(
      decodeBody[CommitRequest](reqs(2).body) ==
        CommitRequest("TRANSACTIONAL", Seq.empty, Some("magic-tx"))
    )
    assert(res == Zombie("Sakura Minamoto"))
  }
}
