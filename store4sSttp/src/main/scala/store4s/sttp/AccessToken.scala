package store4s.sttp

import com.google.auth.oauth2.GoogleCredentials

trait AccessToken {
  def get(): String
}

class AccessTokenImpl() extends AccessToken {
  def newToken() = {
    GoogleCredentials.getApplicationDefault().refreshAccessToken()
  }

  @volatile
  private var token = newToken()

  def expired() = {
    // leave a 6 mins buffer
    System.currentTimeMillis() > token.getExpirationTime().getTime() - 360000
  }

  private def refreshAndGet(): String = synchronized {
    if (expired()) {
      token = newToken()
    }
    token.getTokenValue()
  }

  override def get(): String = {
    if (expired()) refreshAndGet() else token.getTokenValue()
  }
}
