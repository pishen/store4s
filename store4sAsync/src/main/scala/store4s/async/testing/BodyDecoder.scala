package store4s.async.testing

trait BodyDecoder[T] {
  def decode(s: String): T
}

object BodyDecoder {
  def create[T](f: String => T) = new BodyDecoder[T] {
    def decode(s: String): T = f(s)
  }
}
