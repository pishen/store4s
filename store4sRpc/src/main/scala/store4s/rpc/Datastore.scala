package store4s.rpc

import com.google.auth.oauth2.GoogleCredentials
import com.google.auth.oauth2.ServiceAccountCredentials
import com.google.auth.oauth2.UserCredentials

object Datastore {
  def defaultProjectId = GoogleCredentials.getApplicationDefault() match {
    case c: ServiceAccountCredentials => c.getProjectId()
    case c: UserCredentials           => c.getQuotaProjectId()
    case c                            => sys.error(s"Can't find a default project id from $c")
  }
}
