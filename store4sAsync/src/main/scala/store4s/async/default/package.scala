package store4s.async

import com.google.auth.oauth2.GoogleCredentials
import com.google.auth.oauth2.ServiceAccountCredentials
import com.google.auth.oauth2.UserCredentials
import sttp.client3.HttpURLConnectionBackend

import model.PartitionId

package object default {
  val ds = Datastore(
    GoogleCredentials.getApplicationDefault(),
    HttpURLConnectionBackend()
  )
  implicit val partitionId = ds.credentials match {
    case c: ServiceAccountCredentials => PartitionId(c.getProjectId(), None)
    case c: UserCredentials           => PartitionId(c.getQuotaProjectId(), None)
    case _                            => sys.error("Can't find a default project id.")
  }
}
