package store4s.async

import cats.Functor
import cats.syntax.all._
import com.google.auth.oauth2.GoogleCredentials
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

import scala.reflect.runtime.universe._

case class Datastore[F[_]: Functor, P](
    credentials: GoogleCredentials,
    backend: SttpBackend[F, P]
) {
  def authRequest = {
    credentials.refreshIfExpired()
    val accessToken = credentials.getAccessToken().getTokenValue()
    basicRequest.auth.bearer(accessToken)
  }

  def buildUri(method: String)(implicit partitionId: PartitionId) =
    uri"https://datastore.googleapis.com/v1/projects/${partitionId.projectId}:${method}"

  def allocateIds[A: WeakTypeTag](objs: A*)(implicit
      partitionId: PartitionId,
      serializer: BodySerializer[AllocateIdBody],
      respAs: RespAs[AllocateIdBody],
      enc: EntityEncoder[A]
  ) = {
    val kind = weakTypeOf[A].typeSymbol.name.toString()
    val path = Seq(PathElement(kind, None, None))
    val body = AllocateIdBody(Seq.fill(objs.size)(Key(partitionId, path)))
    authRequest
      .body(body)
      .post(buildUri("allocateIds"))
      .response(respAs.value.getRight)
      .send(backend)
      .map { resp =>
        resp.body.keys.zip(objs).map { case (key, obj) =>
          enc.encode(obj, Some(key), Set.empty[String])
        }
      }
  }

  def commit(mutations: Seq[Mutation])(implicit
      partitionId: PartitionId,
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
      partitionId: PartitionId,
      serializer: BodySerializer[CommitRequest],
      respAs: RespAs[CommitResponse]
  ) = commit(entities.map(entity => Mutation(insert = Some(entity))))

  def upsert(entities: Entity*)(implicit
      partitionId: PartitionId,
      serializer: BodySerializer[CommitRequest],
      respAs: RespAs[CommitResponse]
  ) = commit(entities.map(entity => Mutation(upsert = Some(entity))))

  def update(entities: Entity*)(implicit
      partitionId: PartitionId,
      serializer: BodySerializer[CommitRequest],
      respAs: RespAs[CommitResponse]
  ) = commit(entities.map(entity => Mutation(update = Some(entity))))

  def delete(keys: Key*)(implicit
      partitionId: PartitionId,
      serializer: BodySerializer[CommitRequest],
      respAs: RespAs[CommitResponse]
  ) = commit(keys.map(key => Mutation(delete = Some(key))))

  def deleteById[A: WeakTypeTag](ids: Long*)(implicit
      partitionId: PartitionId,
      serializer: BodySerializer[CommitRequest],
      respAs: RespAs[CommitResponse]
  ) = {
    val kind = weakTypeOf[A].typeSymbol.name.toString()
    val keys = ids.map(id =>
      Key(partitionId, Seq(PathElement(kind, Some(id.toString), None)))
    )
    delete(keys: _*)
  }

  def deleteByName[A: WeakTypeTag](names: String*)(implicit
      partitionId: PartitionId,
      serializer: BodySerializer[CommitRequest],
      respAs: RespAs[CommitResponse]
  ) = {
    val kind = weakTypeOf[A].typeSymbol.name.toString()
    val keys = names.map(name =>
      Key(partitionId, Seq(PathElement(kind, None, Some(name))))
    )
    delete(keys: _*)
  }

  def lookup(keys: Key*)(implicit
      partitionId: PartitionId,
      readConsistency: ReadConsistency.Value,
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

  def lookupById[A: WeakTypeTag](ids: Long*)(implicit
      partitionId: PartitionId,
      readConsistency: ReadConsistency.Value,
      serializer: BodySerializer[LookupRequest],
      respAs: RespAs[LookupResponse],
      dec: EntityDecoder[A]
  ) = {
    val kind = weakTypeOf[A].typeSymbol.name.toString()
    val keys = ids.map(id =>
      Key(partitionId, Seq(PathElement(kind, Some(id.toString), None)))
    )
    lookup(keys: _*).map(_.map(e => dec.decode(e).toTry.get))
  }

  def lookupByName[A: WeakTypeTag](names: String*)(implicit
      partitionId: PartitionId,
      readConsistency: ReadConsistency.Value,
      serializer: BodySerializer[LookupRequest],
      respAs: RespAs[LookupResponse],
      dec: EntityDecoder[A]
  ) = {
    val kind = weakTypeOf[A].typeSymbol.name.toString()
    val keys = names.map(name =>
      Key(partitionId, Seq(PathElement(kind, None, Some(name))))
    )
    lookup(keys: _*).map(_.map(e => dec.decode(e).toTry.get))
  }

  def runQuery[S <: Selector](query: Query[S])(implicit
      partitionId: PartitionId,
      readConsistency: ReadConsistency.Value,
      serializer: BodySerializer[RunQueryRequest],
      respAs: RespAs[RunQueryResponse]
  ) = {
    val body = RunQueryRequest(
      partitionId,
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
