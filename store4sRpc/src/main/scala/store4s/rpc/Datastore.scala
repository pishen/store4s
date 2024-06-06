package store4s.rpc

import com.google.auth.oauth2.GoogleCredentials
import com.google.auth.oauth2.ServiceAccountCredentials
import com.google.auth.oauth2.UserCredentials
import com.google.datastore.v1.datastore.CommitRequest
import com.google.datastore.v1.datastore.CommitRequest.Mode.TRANSACTIONAL
import com.google.datastore.v1.datastore.DatastoreGrpc
import com.google.datastore.v1.datastore.LookupRequest
import com.google.datastore.v1.datastore.Mutation
import com.google.datastore.v1.datastore.Mutation.Operation.Delete
import com.google.datastore.v1.datastore.Mutation.Operation.Insert
import com.google.datastore.v1.datastore.Mutation.Operation.Update
import com.google.datastore.v1.datastore.Mutation.Operation.Upsert
import com.google.datastore.v1.datastore.TransactionOptions
import com.google.datastore.v1.datastore.TransactionOptions.ReadWrite
import com.google.datastore.v1.entity.Entity
import com.google.datastore.v1.entity.Key
import com.google.datastore.v1.entity.Key.PathElement
import com.google.datastore.v1.entity.Key.PathElement.IdType
import com.google.datastore.v1.entity.PartitionId
import io.grpc.ManagedChannelBuilder
import io.grpc.auth.MoreCallCredentials

import scala.concurrent.ExecutionContext
import scala.reflect.runtime.universe._

case class Datastore(
    projectId: String = Datastore.defaultProjectId,
    databaseId: String = "",
    namespaceId: String = "",
    host: String = "datastore.googleapis.com",
    port: Int = 443
) {
  val credentials = GoogleCredentials.getApplicationDefault()
  val channel = ManagedChannelBuilder.forAddress(host, port).build()
  val stub = DatastoreGrpc
    .stub(channel)
    .withCallCredentials(MoreCallCredentials.from(credentials))

  def commit(ops: Seq[Mutation.Operation]) = stub.commit(
    CommitRequest()
      .withProjectId(projectId)
      .withDatabaseId(databaseId)
      .withMode(TRANSACTIONAL)
      .withSingleUseTransaction(TransactionOptions().withReadWrite(ReadWrite()))
      .withMutations(ops.map(op => Mutation(operation = op)))
  )

  def buildKey[T: WeakTypeTag](idType: IdType) = {
    val kind = weakTypeOf[T].typeSymbol.name.toString
    Key()
      .withPartitionId(
        PartitionId(
          projectId = projectId,
          databaseId = databaseId,
          namespaceId = namespaceId
        )
      )
      .addPath(PathElement(kind = kind, idType = idType))
  }

  def insert(entities: Entity*) = commit(entities.map(e => Insert(e)))
  def upsert(entities: Entity*) = commit(entities.map(e => Upsert(e)))
  def update(entities: Entity*) = commit(entities.map(e => Update(e)))
  def deleteById[T: WeakTypeTag](ids: Long*) = commit(
    ids.map(id => Delete(buildKey[T](IdType.Id(id))))
  )
  def deleteByName[T: WeakTypeTag](names: String*) = commit(
    names.map(name => Delete(buildKey[T](IdType.Name(name))))
  )

  def lookup[T: Decoder](keys: Seq[Key])(implicit ec: ExecutionContext) = {
    val dec = implicitly[Decoder[T]]
    val req = LookupRequest()
      .withProjectId(projectId)
      .withDatabaseId(databaseId)
      .withKeys(keys)
    stub.lookup(req).map(_.found.map(er => dec.decodeEntity(er.getEntity)))
  }

  def lookupById[T: WeakTypeTag: Decoder](ids: Long*)(implicit
      ec: ExecutionContext
  ) = lookup(ids.map(id => buildKey[T](IdType.Id(id))))

  def lookupByName[T: WeakTypeTag: Decoder](names: String*)(implicit
      ec: ExecutionContext
  ) = lookup(names.map(name => buildKey[T](IdType.Name(name))))
}

object Datastore {
  lazy val defaultProjectId = GoogleCredentials.getApplicationDefault() match {
    case c: ServiceAccountCredentials => c.getProjectId()
    case c: UserCredentials           => c.getQuotaProjectId()
    case c                            => sys.error(s"Can't find a default project id from $c")
  }
}
