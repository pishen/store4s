package store4s.rpc

import com.google.datastore.v1.datastore.AllocateIdsRequest
import com.google.datastore.v1.datastore.AllocateIdsResponse
import com.google.datastore.v1.datastore.BeginTransactionRequest
import com.google.datastore.v1.datastore.BeginTransactionResponse
import com.google.datastore.v1.datastore.CommitRequest
import com.google.datastore.v1.datastore.CommitRequest.Mode.TRANSACTIONAL
import com.google.datastore.v1.datastore.CommitResponse
import com.google.datastore.v1.datastore.DatastoreGrpc
import com.google.datastore.v1.datastore.LookupRequest
import com.google.datastore.v1.datastore.LookupResponse
import com.google.datastore.v1.datastore.Mutation
import com.google.datastore.v1.datastore.Mutation.Operation.Delete
import com.google.datastore.v1.datastore.Mutation.Operation.Insert
import com.google.datastore.v1.datastore.Mutation.Operation.Update
import com.google.datastore.v1.datastore.Mutation.Operation.Upsert
import com.google.datastore.v1.datastore.ReadOptions
import com.google.datastore.v1.datastore.ReserveIdsRequest
import com.google.datastore.v1.datastore.ReserveIdsResponse
import com.google.datastore.v1.datastore.RollbackRequest
import com.google.datastore.v1.datastore.RollbackResponse
import com.google.datastore.v1.datastore.RunAggregationQueryRequest
import com.google.datastore.v1.datastore.RunAggregationQueryResponse
import com.google.datastore.v1.datastore.RunQueryRequest
import com.google.datastore.v1.datastore.RunQueryResponse
import com.google.datastore.v1.datastore.TransactionOptions
import com.google.datastore.v1.datastore.TransactionOptions.ReadWrite
import com.google.datastore.v1.entity.Entity
import com.google.datastore.v1.entity.Key
import com.google.datastore.v1.entity.PartitionId
import com.google.datastore.v1.entity.Value
import com.google.datastore.v1.query.EntityResult
import com.google.datastore.v1.query.QueryResultBatch
import com.google.protobuf.ByteString
import io.grpc.ServerBuilder
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AsyncFlatSpec

import scala.concurrent.Future

case class Recorder(port: Int) extends DatastoreGrpc.Datastore {

  val server = ServerBuilder
    .forPort(port)
    .addService(
      DatastoreGrpc.bindService(this, scala.concurrent.ExecutionContext.global)
    )
    .build()

  var lookupRequest: LookupRequest = null
  var runQueryRequest: RunQueryRequest = null
  var beginTransactionRequest: BeginTransactionRequest = null
  var commitRequest: CommitRequest = null
  var rollbackRequest: RollbackRequest = null
  var allocateIdsRequest: AllocateIdsRequest = null

  def clear() = {
    lookupRequest = null
    runQueryRequest = null
    beginTransactionRequest = null
    commitRequest = null
    rollbackRequest = null
    allocateIdsRequest = null
  }

  def now = System.currentTimeMillis()

  override def lookup(request: LookupRequest): Future[LookupResponse] = {
    lookupRequest = request
    Future.successful(
      LookupResponse().addFound(
        EntityResult().withEntity(
          Entity().addProperties(
            "name" -> Value().withStringValue("Sakura Minamoto")
          )
        )
      )
    )
  }

  override def runQuery(request: RunQueryRequest): Future[RunQueryResponse] = {
    runQueryRequest = request
    if (request.getQuery.startCursor == ByteString.EMPTY) {
      Future.successful(
        RunQueryResponse().withBatch(
          QueryResultBatch()
            .addEntityResults(
              EntityResult().withEntity(
                Entity().addProperties("i" -> Value().withIntegerValue(1))
              )
            )
            .withEndCursor(ByteString.copyFromUtf8("token"))
            .withMoreResults(QueryResultBatch.MoreResultsType.NOT_FINISHED)
        )
      )
    } else {
      Future.successful(
        RunQueryResponse().withBatch(
          QueryResultBatch()
            .addEntityResults(
              EntityResult().withEntity(
                Entity().addProperties("i" -> Value().withIntegerValue(2))
              )
            )
        )
      )
    }
  }

  override def runAggregationQuery(
      request: RunAggregationQueryRequest
  ): Future[RunAggregationQueryResponse] = ???

  override def beginTransaction(
      request: BeginTransactionRequest
  ): Future[BeginTransactionResponse] = {
    beginTransactionRequest = request
    Future.successful(
      BeginTransactionResponse(transaction = ByteString.copyFromUtf8("tx-id"))
    )
  }

  override def commit(request: CommitRequest): Future[CommitResponse] = {
    commitRequest = request
    Future.successful(CommitResponse())
  }

  override def rollback(request: RollbackRequest): Future[RollbackResponse] = {
    rollbackRequest = request
    Future.successful(RollbackResponse())
  }

  override def allocateIds(
      request: AllocateIdsRequest
  ): Future[AllocateIdsResponse] = {
    allocateIdsRequest = request
    Future.successful(
      AllocateIdsResponse().addKeys(
        Key().addPath(Key.PathElement().withId(1)),
        Key().addPath(Key.PathElement().withId(2)),
        Key().addPath(Key.PathElement().withId(3))
      )
    )
  }

  override def reserveIds(
      request: ReserveIdsRequest
  ): Future[ReserveIdsResponse] = ???

}

class DatastoreSpec extends AsyncFlatSpec with BeforeAndAfterAll {
  val recorder = Recorder(8080)
  override protected def beforeAll(): Unit = {
    recorder.server.start(): Unit
  }
  override protected def afterAll(): Unit = {
    recorder.server.shutdown(): Unit
  }
  val ds = Datastore(host = "localhost", port = 8080, devMode = true)

  "Datastore" should "support allocateIds" in {
    case class User(name: String)
    val key = Key()
      .withPartitionId(PartitionId(projectId = ds.projectId))
      .addPath(Key.PathElement(kind = "User"))
    val req = AllocateIdsRequest()
      .withProjectId(ds.projectId)
      .withKeys(Seq.fill(3)(key))

    ds.allocateIds[User](3).map { ids =>
      assert(ids == Seq(1, 2, 3))
      assert(recorder.allocateIdsRequest == req)
    }
  }

  it should "support insert" in {
    case class Zombie(name: String)
    val e = Zombie("Sakura Minamoto").asEntity("heroine")
    val req = CommitRequest()
      .withProjectId(ds.projectId)
      .withMode(TRANSACTIONAL)
      .withSingleUseTransaction(TransactionOptions().withReadWrite(ReadWrite()))
      .addMutations(Mutation(operation = Insert(e)))

    ds.insert(e).map { _ =>
      assert(recorder.commitRequest == req)
    }
  }

  it should "support upsert" in {
    case class Zombie(name: String)
    val e = Zombie("Sakura Minamoto").asEntity("heroine")
    val req = CommitRequest()
      .withProjectId(ds.projectId)
      .withMode(TRANSACTIONAL)
      .withSingleUseTransaction(TransactionOptions().withReadWrite(ReadWrite()))
      .addMutations(Mutation(operation = Upsert(e)))

    ds.upsert(e).map { _ =>
      assert(recorder.commitRequest == req)
    }
  }

  it should "support update" in {
    case class Zombie(name: String)
    val e = Zombie("Sakura Minamoto").asEntity("heroine")
    val req = CommitRequest()
      .withProjectId(ds.projectId)
      .withMode(TRANSACTIONAL)
      .withSingleUseTransaction(TransactionOptions().withReadWrite(ReadWrite()))
      .addMutations(Mutation(operation = Update(e)))

    ds.update(e).map { _ =>
      assert(recorder.commitRequest == req)
    }
  }

  it should "support deleteById" in {
    case class Zombie(name: String)
    val key = Key()
      .withPartitionId(PartitionId(projectId = ds.projectId))
      .addPath(Key.PathElement(kind = "Zombie").withId(123))
    val req = CommitRequest()
      .withProjectId(ds.projectId)
      .withMode(TRANSACTIONAL)
      .withSingleUseTransaction(TransactionOptions().withReadWrite(ReadWrite()))
      .addMutations(Mutation(operation = Delete(key)))

    ds.deleteById[Zombie](123).map { _ =>
      assert(recorder.commitRequest == req)
    }
  }

  it should "support deleteByName" in {
    case class Zombie(name: String)
    val key = Key()
      .withPartitionId(PartitionId(projectId = ds.projectId))
      .addPath(Key.PathElement(kind = "Zombie").withName("heroine"))
    val req = CommitRequest()
      .withProjectId(ds.projectId)
      .withMode(TRANSACTIONAL)
      .withSingleUseTransaction(TransactionOptions().withReadWrite(ReadWrite()))
      .addMutations(Mutation(operation = Delete(key)))

    ds.deleteByName[Zombie]("heroine").map { _ =>
      assert(recorder.commitRequest == req)
    }
  }

  it should "support lookupById" in {
    case class Zombie(name: String)
    val key = Key()
      .withPartitionId(PartitionId(projectId = ds.projectId))
      .addPath(Key.PathElement(kind = "Zombie").withId(123))
    val req = LookupRequest().withProjectId(ds.projectId).addKeys(key)

    ds.lookupById[Zombie](123).map { seq =>
      assert(recorder.lookupRequest == req)
      assert(seq.head == Zombie("Sakura Minamoto"))
    }
  }

  it should "support lookupByName" in {
    case class Zombie(name: String)
    val key = Key()
      .withPartitionId(PartitionId(projectId = ds.projectId))
      .addPath(Key.PathElement(kind = "Zombie").withName("heroine"))
    val req = LookupRequest().withProjectId(ds.projectId).addKeys(key)

    ds.lookupByName[Zombie]("heroine").map { seq =>
      assert(recorder.lookupRequest == req)
      assert(seq.head == Zombie("Sakura Minamoto"))
    }
  }

  it should "support runQuery with NOT_FINISHED" in {
    case class Page(i: Int)
    val query = Query.from[Page]

    val req = RunQueryRequest()
      .withProjectId(ds.projectId)
      .withPartitionId(PartitionId(projectId = ds.projectId))
      .withQuery(query.startCursor(ByteString.copyFromUtf8("token")).q)

    query.run(ds).map { res =>
      assert(recorder.runQueryRequest == req)
      assert(res.toSeq == Seq(Page(1), Page(2)))
    }
  }

  it should "support transaction" in {
    case class Zombie(name: String)

    val txReq = BeginTransactionRequest()
      .withProjectId(ds.projectId)
      .withTransactionOptions(TransactionOptions().withReadWrite(ReadWrite()))
    val key = Key()
      .withPartitionId(PartitionId(projectId = ds.projectId))
      .addPath(Key.PathElement(kind = "Zombie").withId(123))
    val lookupReq = LookupRequest()
      .withProjectId(ds.projectId)
      .withReadOptions(
        ReadOptions().withTransaction(ByteString.copyFromUtf8("tx-id"))
      )
      .addKeys(key)
    val commitReq = CommitRequest()
      .withProjectId(ds.projectId)
      .withMode(TRANSACTIONAL)
      .withTransaction(ByteString.copyFromUtf8("tx-id"))
      .addMutations(Mutation(operation = Update(Zombie("ABC").asEntity(123))))

    val f = ds.transaction { tx =>
      for {
        z <- tx.lookupById[Zombie](123).map(_.head)
        e = z.copy(name = "ABC").asEntity(123)
        _ <- tx.update(e)
      } yield z
    }
    f.map { z =>
      assert(recorder.beginTransactionRequest == txReq)
      assert(recorder.lookupRequest == lookupReq)
      assert(z == Zombie("Sakura Minamoto"))
      assert(recorder.commitRequest == commitReq)
    }
  }

  it should "rollback automatically when transaction failed" in {
    recorder.clear()

    case class Zombie(name: String)

    val txReq = BeginTransactionRequest()
      .withProjectId(ds.projectId)
      .withTransactionOptions(TransactionOptions().withReadWrite(ReadWrite()))
    val rollbackReq = RollbackRequest()
      .withProjectId(ds.projectId)
      .withTransaction(ByteString.copyFromUtf8("tx-id"))

    val f = ds.transaction[Unit] { _ =>
      sys.error("error")
    }
    f.failed.map { error =>
      assert(recorder.beginTransactionRequest == txReq)
      assert(recorder.commitRequest == null)
      assert(recorder.rollbackRequest == rollbackReq)
      assert(error.getMessage == "error")
    }
  }
}
