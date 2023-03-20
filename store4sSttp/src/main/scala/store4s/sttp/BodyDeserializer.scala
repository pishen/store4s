package store4s.sttp

import sttp.client3.ResponseAs
import sttp.client3.ResponseException

// Since Either[+A, +B] is covariant on B, we use this trait to prevent compiler
// from finding ResponseAs[Either[ResponseException[String, Exception], Nothing], Any]
trait BodyDeserializer[B] {
  def value: ResponseAs[Either[ResponseException[String, Exception], B], Any]
}

object BodyDeserializer {
  def from[B](
      v: => ResponseAs[Either[ResponseException[String, Exception], B], Any]
  ) = new BodyDeserializer[B] {
    def value = v
  }
}
