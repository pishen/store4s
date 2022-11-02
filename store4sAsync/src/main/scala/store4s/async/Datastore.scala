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

  def allocateId[A: WeakTypeTag](implicit
      partitionId: PartitionId,
      serializer: BodySerializer[AllocateIdBody],
      respAs: RespAs[AllocateIdBody]
  ) = {
    val kind = weakTypeOf[A].typeSymbol.name.toString()
    val path = Seq(PathElement(kind, None, None))
    val body = AllocateIdBody(Seq(Key(partitionId, path)))
    authRequest
      .body(body)
      .post(buildUri("allocateIds"))
      .response(respAs.value.getRight)
      .send(backend)
      .map(_.body.keys.head.path.head.id.get.toLong)
  }
}
