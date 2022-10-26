package store4s.async

import cats.Functor
import cats.syntax.all._
import com.google.auth.oauth2.GoogleCredentials
import com.google.auth.oauth2.ServiceAccountCredentials
import com.google.auth.oauth2.UserCredentials
import sttp.client3._

import scala.reflect.runtime.universe._

import model._

case class Datastore[F[_]: Functor, P](
    projectId: String,
    namespace: Option[String],
    credentials: GoogleCredentials,
    backend: SttpBackend[F, P]
) {
  def authRequest = {
    credentials.refreshIfExpired()
    val accessToken = credentials.getAccessToken().getTokenValue()
    basicRequest.auth.bearer(accessToken)
  }

  def allocateId[A: WeakTypeTag](implicit
      serializer: BodySerializer[AllocateIdBody],
      respAs: RespAs[AllocateIdBody]
  ) = {
    val kind = weakTypeOf[A].typeSymbol.name.toString()
    val partitionId = PartitionId(projectId, namespace)
    val path = Seq(PathElement(kind, None, None))
    val body = AllocateIdBody(Seq(Key(partitionId, path)))
    authRequest
      .body(body)
      .post(
        uri"https://datastore.googleapis.com/v1/projects/${projectId}:allocateIds"
      )
      .response(respAs.value.getRight)
      .send(backend)
      .map(_.body.keys.head.path.head.id.get.toLong)
  }
}

object Datastore {
  def defaultInstance = {
    val credentials = GoogleCredentials.getApplicationDefault()
    val projectId = credentials match {
      case c: ServiceAccountCredentials => c.getProjectId()
      case c: UserCredentials           => c.getQuotaProjectId()
      case _                            => sys.error("Can't find a default project id.")
    }
    val backend = HttpURLConnectionBackend()
    Datastore(projectId, None, credentials, backend)
  }
}
