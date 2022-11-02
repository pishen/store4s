package store4s.async

import sttp.client3.ResponseAs
import sttp.client3.ResponseException

// Since Either[+A, +B] is covariant on B, we use this trait to prevent compiler
// from finding ResponseAs[Either[ResponseException[String, Exception], Nothing], Any]
trait RespAs[B] {
  def value: ResponseAs[Either[ResponseException[String, Exception], B], Any]
}

object RespAs {
  def create[B](
      v: => ResponseAs[Either[ResponseException[String, Exception], B], Any]
  ) = new RespAs[B] {
    def value = v
  }
}
