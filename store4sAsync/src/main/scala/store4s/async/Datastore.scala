package store4s.async

import cats.Functor
import cats.syntax.all._
import com.google.auth.oauth2.GoogleCredentials
import com.google.auth.oauth2.ServiceAccountCredentials
import com.google.auth.oauth2.UserCredentials
import store4s.async.model.AllocateIdBody
import store4s.async.model.CommitRequest
import store4s.async.model.CommitResponse
import store4s.async.model.Entity
import store4s.async.model.EntityResult
import store4s.async.model.Key
import store4s.async.model.LookupRequest
import store4s.async.model.LookupResponse
import store4s.async.model.Mutation
import store4s.async.model.PartitionId
import store4s.async.model.PathElement
import store4s.async.model.ReadOptions
import store4s.async.model.RunQueryRequest
import store4s.async.model.RunQueryResponse
import sttp.client3._

import java.util.concurrent.atomic.AtomicReference
import scala.reflect.runtime.universe._

case class AccessToken(tokenValue: String, expirationTime: Long)

case class Datastore[F[_]: Functor, P](
    getToken: () => AccessToken = Datastore.defaultTokenGetter,
    projectId: String = Datastore.defaultProjectId,
    backend: SttpBackend[F, P] = HttpURLConnectionBackend()
) {
  val accessToken = new AtomicReference(getToken())

  def getTokenWithRefresh() = {
    val currentToken = accessToken.get()
    // Try to refresh the token if it's expiring in 6 mins
    if (currentToken.expirationTime - System.currentTimeMillis() < 360000) {
      accessToken.compareAndSet(currentToken, getToken())
    }
    currentToken.tokenValue
  }

  def authRequest = basicRequest.auth.bearer(getTokenWithRefresh())

  def buildUri(method: String) =
    uri"https://datastore.googleapis.com/v1/projects/${projectId}:${method}"

  def allocateIds[A: WeakTypeTag](numOfIds: Int, namespace: String = null)(
      implicit
      serializer: BodySerializer[AllocateIdBody],
      respAs: RespAs[AllocateIdBody]
  ) = {
    val kind = weakTypeOf[A].typeSymbol.name.toString()
    val path = Seq(PathElement(kind, None, None))
    val body = AllocateIdBody(
      Seq.fill(numOfIds)(Key(PartitionId(projectId, Option(namespace)), path))
    )
    authRequest
      .body(body)
      .post(buildUri("allocateIds"))
      .response(respAs.value.getRight)
      .send(backend)
      .map(_.body.keys.map(_.path.head.id.get.toLong))
  }

  def commit(mutations: Seq[Mutation])(implicit
      serializer: BodySerializer[CommitRequest],
      respAs: RespAs[CommitResponse]
  ) = {
    authRequest
      .body(CommitRequest("NON_TRANSACTIONAL", mutations, None))
      .post(buildUri("commit"))
      .response(respAs.value.getRight)
      .send(backend)
      .map(_.body.mutationResults.map(_.key))
  }

  def insert(entities: Entity*)(implicit
      serializer: BodySerializer[CommitRequest],
      respAs: RespAs[CommitResponse]
  ) = commit(entities.map(entity => Mutation(insert = Some(entity))))

  def upsert(entities: Entity*)(implicit
      serializer: BodySerializer[CommitRequest],
      respAs: RespAs[CommitResponse]
  ) = commit(entities.map(entity => Mutation(upsert = Some(entity))))

  def update(entities: Entity*)(implicit
      serializer: BodySerializer[CommitRequest],
      respAs: RespAs[CommitResponse]
  ) = commit(entities.map(entity => Mutation(update = Some(entity))))

  def delete(keys: Key*)(implicit
      serializer: BodySerializer[CommitRequest],
      respAs: RespAs[CommitResponse]
  ) = commit(keys.map(key => Mutation(delete = Some(key))))

  def deleteByIds[A: WeakTypeTag](ids: Seq[Long], namespace: String = null)(
      implicit
      serializer: BodySerializer[CommitRequest],
      respAs: RespAs[CommitResponse]
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
      respAs: RespAs[CommitResponse]
  ) = deleteByIds(Seq(id), namespace)

  def deleteByNames[A: WeakTypeTag](
      names: Seq[String],
      namespace: String = null
  )(implicit
      serializer: BodySerializer[CommitRequest],
      respAs: RespAs[CommitResponse]
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
      respAs: RespAs[CommitResponse]
  ) = deleteByNames(Seq(name), namespace)

  def lookup(keys: Seq[Key], readConsistency: ReadConsistency.Value)(implicit
      serializer: BodySerializer[LookupRequest],
      respAs: RespAs[LookupResponse]
  ) = {
    val body = LookupRequest(
      ReadOptions(Some(readConsistency.toString), None),
      keys
    )
    authRequest
      .body(body)
      .post(buildUri("lookup"))
      .response(respAs.value.getRight)
      .send(backend)
      .map(_.body.found.getOrElse(Seq.empty[EntityResult]).map(_.entity))
  }

  def lookupByIds[A: WeakTypeTag](
      ids: Seq[Long],
      namespace: String = null,
      readConsistency: ReadConsistency.Value = ReadConsistency.STRONG
  )(implicit
      serializer: BodySerializer[LookupRequest],
      respAs: RespAs[LookupResponse],
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
      respAs: RespAs[LookupResponse],
      dec: EntityDecoder[A]
  ) = lookupByIds(Seq(id), namespace, readConsistency).map(_.headOption)

  def lookupByNames[A: WeakTypeTag](
      names: Seq[String],
      namespace: String = null,
      readConsistency: ReadConsistency.Value = ReadConsistency.STRONG
  )(implicit
      serializer: BodySerializer[LookupRequest],
      respAs: RespAs[LookupResponse],
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
      respAs: RespAs[LookupResponse],
      dec: EntityDecoder[A]
  ) = lookupByNames(Seq(name), namespace, readConsistency).map(_.headOption)

  def runQuery[S <: Selector](
      query: Query[S],
      namespace: String = null,
      readConsistency: ReadConsistency.Value = ReadConsistency.STRONG
  )(implicit
      serializer: BodySerializer[RunQueryRequest],
      respAs: RespAs[RunQueryResponse]
  ) = {
    val body = RunQueryRequest(
      PartitionId(projectId, Option(namespace)),
      ReadOptions(Some(readConsistency.toString), None),
      query.query
    )
    authRequest
      .body(body)
      .post(buildUri("runQuery"))
      .response(respAs.value.getRight)
      .send(backend)
      .map(resp => Query.Result[query.selector.R](resp.body.batch))
  }
}

object Datastore {
  def defaultTokenGetter() = {
    val credentials = GoogleCredentials.getApplicationDefault()
    val token = credentials.refreshAccessToken()
    AccessToken(token.getTokenValue(), token.getExpirationTime().getTime())
  }

  def defaultProjectId = GoogleCredentials.getApplicationDefault() match {
    case c: ServiceAccountCredentials => c.getProjectId()
    case c: UserCredentials           => c.getQuotaProjectId()
    case _                            => sys.error("Can't find a default project id.")
  }
}
