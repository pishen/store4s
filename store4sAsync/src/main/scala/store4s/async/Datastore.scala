package store4s.async

import cats.Functor
import cats.syntax.all._
import com.google.auth.oauth2.GoogleCredentials
import sttp.client3._

import scala.reflect.runtime.universe._

import model._

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

  def allocateIds[A: WeakTypeTag](numOfIds: Int)(implicit
      partitionId: PartitionId,
      serializer: BodySerializer[AllocateIdBody],
      respAs: RespAs[AllocateIdBody]
  ) = {
    val kind = weakTypeOf[A].typeSymbol.name.toString()
    val path = Seq(PathElement(kind, None, None))
    val body = AllocateIdBody(Seq.fill(numOfIds)(Key(partitionId, path)))
    authRequest
      .body(body)
      .post(buildUri("allocateIds"))
      .response(respAs.value.getRight)
      .send(backend)
      .map(_.body.keys.map(_.path.head.id.get.toLong))
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
}
