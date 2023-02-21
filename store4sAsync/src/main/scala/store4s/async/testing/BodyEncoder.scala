package store4s.async.testing

trait BodyEncoder[T] {
  def encode(t: T): String
}

object BodyEncoder {
  def create[T](f: T => String) = new BodyEncoder[T] {
    def encode(t: T): String = f(t)
  }
}
