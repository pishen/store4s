package store4s.sttp

import com.google.auth.oauth2.GoogleCredentials
import com.google.auth.oauth2.ServiceAccountCredentials
import com.google.auth.oauth2.UserCredentials
import store4s.sttp.model.{Query => _, _}
import sttp.client3._
import sttp.monad.MonadError
import sttp.monad.syntax._

import scala.reflect.runtime.universe._

trait Datastore[F[_]] {
  val projectId: String

  implicit val responseMonad: MonadError[F]

  def allocateIds[A: WeakTypeTag](numOfIds: Int, namespace: String = null)(
      implicit
      serializer: BodySerializer[AllocateIdBody],
      deserializer: BodyDeserializer[AllocateIdBody]
  ): F[Seq[Long]]

  def commit(mutations: Seq[Mutation], txOpt: Option[String])(implicit
      serializer: BodySerializer[CommitRequest],
      deserializer: BodyDeserializer[CommitResponse]
  ): F[Seq[Option[Key]]]

  def insert(entities: Entity*)(implicit
      serializer: BodySerializer[CommitRequest],
      deserializer: BodyDeserializer[CommitResponse]
  ) = commit(entities.map(entity => Mutation(insert = Some(entity))), None)

  def upsert(entities: Entity*)(implicit
      serializer: BodySerializer[CommitRequest],
      deserializer: BodyDeserializer[CommitResponse]
  ) = commit(entities.map(entity => Mutation(upsert = Some(entity))), None)

  def update(entities: Entity*)(implicit
      serializer: BodySerializer[CommitRequest],
      deserializer: BodyDeserializer[CommitResponse]
  ) = commit(entities.map(entity => Mutation(update = Some(entity))), None)

  def delete(keys: Key*)(implicit
      serializer: BodySerializer[CommitRequest],
      deserializer: BodyDeserializer[CommitResponse]
  ) = commit(keys.map(key => Mutation(delete = Some(key))), None)

  def deleteByIds[A: WeakTypeTag](ids: Seq[Long], namespace: String = null)(
      implicit
      serializer: BodySerializer[CommitRequest],
      deserializer: BodyDeserializer[CommitResponse]
  ) = {
    val kind = weakTypeOf[A].typeSymbol.name.toString()
    val keys = ids.map(id =>
      Key(
        PartitionId(projectId, Option(namespace)),
        Seq(PathElement(kind, Some(id.toString), None))
      )
    )
    delete(keys: _*)
  }

  def deleteById[A: WeakTypeTag](id: Long, namespace: String = null)(implicit
      serializer: BodySerializer[CommitRequest],
      deserializer: BodyDeserializer[CommitResponse]
  ) = deleteByIds(Seq(id), namespace)

  def deleteByNames[A: WeakTypeTag](
      names: Seq[String],
      namespace: String = null
  )(implicit
      serializer: BodySerializer[CommitRequest],
      deserializer: BodyDeserializer[CommitResponse]
  ) = {
    val kind = weakTypeOf[A].typeSymbol.name.toString()
    val keys = names.map(name =>
      Key(
        PartitionId(projectId, Option(namespace)),
        Seq(PathElement(kind, None, Some(name)))
      )
    )
    delete(keys: _*)
  }

  def deleteByName[A: WeakTypeTag](name: String, namespace: String = null)(
      implicit
      serializer: BodySerializer[CommitRequest],
      deserializer: BodyDeserializer[CommitResponse]
  ) = deleteByNames(Seq(name), namespace)

  def lookup(keys: Seq[Key], readConsistency: ReadConsistency.Value)(implicit
      serializer: BodySerializer[LookupRequest],
      deserializer: BodyDeserializer[LookupResponse]
  ): F[Seq[Entity]]

  def lookupByIds[A: WeakTypeTag](
      ids: Seq[Long],
      namespace: String = null,
      readConsistency: ReadConsistency.Value = ReadConsistency.STRONG
  )(implicit
      serializer: BodySerializer[LookupRequest],
      deserializer: BodyDeserializer[LookupResponse],
      dec: EntityDecoder[A]
  ) = {
    val kind = weakTypeOf[A].typeSymbol.name.toString()
    val keys = ids.map(id =>
      Key(
        PartitionId(projectId, Option(namespace)),
        Seq(PathElement(kind, Some(id.toString), None))
      )
    )
    lookup(keys, readConsistency).map(_.map(e => dec.decode(e).toTry.get))
  }

  def lookupById[A: WeakTypeTag](
      id: Long,
      namespace: String = null,
      readConsistency: ReadConsistency.Value = ReadConsistency.STRONG
  )(implicit
      serializer: BodySerializer[LookupRequest],
      deserializer: BodyDeserializer[LookupResponse],
      dec: EntityDecoder[A]
  ) = lookupByIds(Seq(id), namespace, readConsistency).map(_.headOption)

  def lookupByNames[A: WeakTypeTag](
      names: Seq[String],
      namespace: String = null,
      readConsistency: ReadConsistency.Value = ReadConsistency.STRONG
  )(implicit
      serializer: BodySerializer[LookupRequest],
      deserializer: BodyDeserializer[LookupResponse],
      dec: EntityDecoder[A]
  ) = {
    val kind = weakTypeOf[A].typeSymbol.name.toString()
    val keys = names.map(name =>
      Key(
        PartitionId(projectId, Option(namespace)),
        Seq(PathElement(kind, None, Some(name)))
      )
    )
    lookup(keys, readConsistency).map(_.map(e => dec.decode(e).toTry.get))
  }

  def lookupByName[A: WeakTypeTag](
      name: String,
      namespace: String = null,
      readConsistency: ReadConsistency.Value = ReadConsistency.STRONG
  )(implicit
      serializer: BodySerializer[LookupRequest],
      deserializer: BodyDeserializer[LookupResponse],
      dec: EntityDecoder[A]
  ) = lookupByNames(Seq(name), namespace, readConsistency).map(_.headOption)

  def runQuery[S <: Selector](
      query: Query[S],
      namespace: String = null,
      readConsistency: ReadConsistency.Value = ReadConsistency.STRONG
  )(implicit
      serializer: BodySerializer[RunQueryRequest],
      deserializer: BodyDeserializer[RunQueryResponse]
  ): F[Query.Result[query.selector.R]]

  def transaction[R](f: Transaction[F] => F[(R, Seq[Mutation])])(implicit
      txSerializer: BodySerializer[BeginTransactionRequest],
      txDeserializer: BodyDeserializer[BeginTransactionResponse],
      commitSerializer: BodySerializer[CommitRequest],
      commitDeserializer: BodyDeserializer[CommitResponse],
      rollbackSerializer: BodySerializer[RollbackRequest]
  ): F[R]

  def transactionReadOnly[R](f: Transaction[F] => F[R])(implicit
      txSerializer: BodySerializer[BeginTransactionRequest],
      txDeserializer: BodyDeserializer[BeginTransactionResponse],
      commitSerializer: BodySerializer[CommitRequest],
      commitDeserializer: BodyDeserializer[CommitResponse],
      rollbackSerializer: BodySerializer[RollbackRequest]
  ): F[R]
}

case class DatastoreImpl[F[_], P](
    accessToken: AccessToken,
    projectId: String,
    backend: SttpBackend[F, P]
) extends Datastore[F] {
  implicit val responseMonad = backend.responseMonad

  def authRequest = basicRequest.auth.bearer(accessToken.get())

  def buildUri(method: String) =
    uri"https://datastore.googleapis.com/v1/projects/${projectId}:${method}"

  def allocateIds[A: WeakTypeTag](numOfIds: Int, namespace: String = null)(
      implicit
      serializer: BodySerializer[AllocateIdBody],
      deserializer: BodyDeserializer[AllocateIdBody]
  ) = {
    val kind = weakTypeOf[A].typeSymbol.name.toString()
    val path = Seq(PathElement(kind, None, None))
    val body = AllocateIdBody(
      Seq.fill(numOfIds)(Key(PartitionId(projectId, Option(namespace)), path))
    )
    authRequest
      .body(body)
      .post(buildUri("allocateIds"))
      .response(deserializer.value.getRight)
      .send(backend)
      .map(_.body.keys.map(_.path.head.id.get.toLong))
  }

  def commit(mutations: Seq[Mutation], txOpt: Option[String])(implicit
      serializer: BodySerializer[CommitRequest],
      deserializer: BodyDeserializer[CommitResponse]
  ) = {
    val mode = if (txOpt.isDefined) "TRANSACTIONAL" else "NON_TRANSACTIONAL"
    authRequest
      .body(CommitRequest(mode, mutations, txOpt))
      .post(buildUri("commit"))
      .response(deserializer.value.getRight)
      .send(backend)
      .map(_.body.mutationResults.map(_.key))
  }

  def lookup(keys: Seq[Key], readConsistency: ReadConsistency.Value)(implicit
      serializer: BodySerializer[LookupRequest],
      deserializer: BodyDeserializer[LookupResponse]
  ) = {
    val body = LookupRequest(
      ReadOptions(Some(readConsistency.toString), None),
      keys
    )
    authRequest
      .body(body)
      .post(buildUri("lookup"))
      .response(deserializer.value.getRight)
      .send(backend)
      .map(_.body.found.getOrElse(Seq.empty[EntityResult]).map(_.entity))
  }

  def runQuery[S <: Selector](
      query: Query[S],
      namespace: String = null,
      readConsistency: ReadConsistency.Value = ReadConsistency.STRONG
  )(implicit
      serializer: BodySerializer[RunQueryRequest],
      deserializer: BodyDeserializer[RunQueryResponse]
  ) = {
    val body = RunQueryRequest(
      PartitionId(projectId, Option(namespace)),
      ReadOptions(Some(readConsistency.toString), None),
      query.query
    )
    authRequest
      .body(body)
      .post(buildUri("runQuery"))
      .response(deserializer.value.getRight)
      .send(backend)
      .map(resp => Query.Result[query.selector.R](resp.body.batch))
  }

  def beginTransaction(readOnly: Boolean)(implicit
      serializer: BodySerializer[BeginTransactionRequest],
      deserializer: BodyDeserializer[BeginTransactionResponse]
  ) = {
    authRequest
      .body(
        BeginTransactionRequest(
          if (readOnly) TransactionOptions(None, Some(ReadOnly()))
          else TransactionOptions(Some(ReadWrite(None)), None)
        )
      )
      .post(buildUri("beginTransaction"))
      .response(deserializer.value.getRight)
      .send(backend)
  }

  def transaction[R](f: Transaction[F] => F[(R, Seq[Mutation])])(implicit
      txSerializer: BodySerializer[BeginTransactionRequest],
      txDeserializer: BodyDeserializer[BeginTransactionResponse],
      commitSerializer: BodySerializer[CommitRequest],
      commitDeserializer: BodyDeserializer[CommitResponse],
      rollbackSerializer: BodySerializer[RollbackRequest]
  ) = {
    beginTransaction(false).flatMap { resp =>
      val txId = resp.body.transaction
      f(TransactionImpl(txId, this))
        .flatMap { case (res, mutations) =>
          commit(mutations, Some(txId)).map(_ => res)
        }
        .handleError { case t: Throwable =>
          authRequest
            .body(RollbackRequest(txId))
            .post(buildUri("rollback"))
            .response(asString.getRight)
            .send(backend)
            .flatMap(_ => MonadError[F].error(t))
        }
    }
  }

  def transactionReadOnly[R](f: Transaction[F] => F[R])(implicit
      txSerializer: BodySerializer[BeginTransactionRequest],
      txDeserializer: BodyDeserializer[BeginTransactionResponse],
      commitSerializer: BodySerializer[CommitRequest],
      commitDeserializer: BodyDeserializer[CommitResponse],
      rollbackSerializer: BodySerializer[RollbackRequest]
  ) = {
    beginTransaction(true).flatMap { resp =>
      val txId = resp.body.transaction
      f(TransactionImpl(txId, this))
        .flatMap { res =>
          commit(Seq.empty[Mutation], Some(txId)).map(_ => res)
        }
        .handleError { case t: Throwable =>
          authRequest
            .body(RollbackRequest(txId))
            .post(buildUri("rollback"))
            .response(asString.getRight)
            .send(backend)
            .flatMap(_ => MonadError[F].error(t))
        }
    }
  }
}

object Datastore {
  def defaultProjectId = GoogleCredentials.getApplicationDefault() match {
    case c: ServiceAccountCredentials => c.getProjectId()
    case c: UserCredentials           => c.getQuotaProjectId()
    case _                            => sys.error("Can't find a default project id.")
  }

  def apply[F[_], P](
      accessToken: AccessToken = new AccessTokenImpl(),
      projectId: String = defaultProjectId,
      backend: SttpBackend[F, P] = HttpURLConnectionBackend()
  ): Datastore[F] = new DatastoreImpl(accessToken, projectId, backend)
}
