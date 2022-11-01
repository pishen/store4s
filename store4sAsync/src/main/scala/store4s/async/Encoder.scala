package store4s.async

import java.time.Instant
import java.time.ZoneId
import java.util.Base64

import model._

trait ValueEncoder[T] { self =>
  def encode(t: T, excludeFromIndexes: Boolean): Value

  def contramap[A](f: A => T) = new ValueEncoder[A] {
    def encode(a: A, excludeFromIndexes: Boolean): Value =
      self.encode(f(a), excludeFromIndexes)
  }
}

object ValueEncoder {
  def apply[T](implicit enc: ValueEncoder[T]) = enc

  def create[T](f: (T, Boolean) => Value) = new ValueEncoder[T] {
    def encode(t: T, excludeFromIndexes: Boolean): Value =
      f(t, excludeFromIndexes)
  }

  implicit val booleanEncoder =
    create[Boolean]((t, e) => Value(e, booleanValue = Some(t)))
  implicit val intEncoder =
    create[Int]((t, e) => Value(e, integerValue = Some(t.toString)))
  implicit val longEncoder =
    create[Long]((t, e) => Value(e, integerValue = Some(t.toString)))
  implicit val doubleEncoder =
    create[Double]((t, e) => Value(e, doubleValue = Some(t)))
  implicit val instantEncoder =
    create[Instant]((t, e) =>
      Value(e, timestampValue = Some(t.atZone(ZoneId.of("Z")).toString))
    )
  implicit val keyEncoder = create[Key]((t, e) => Value(e, keyValue = Some(t)))
  implicit val stringEncoder =
    create[String]((t, e) => Value(e, stringValue = Some(t)))
  implicit val bytesEncoder =
    create[Array[Byte]]((t, e) =>
      Value(e, blobValue = Some(Base64.getEncoder().encodeToString(t)))
    )
  implicit val latLngEncoder =
    create[LatLng]((t, e) => Value(e, geoPointValue = Some(t)))
  implicit def entityEncoder[T](implicit enc: EntityEncoder[T]) =
    create[T]((t, e) =>
      Value(e, entityValue = Some(enc.encode(t, None, Set.empty)))
    )
  implicit def seqEncoder[T](implicit enc: ValueEncoder[T]) =
    create[Seq[T]]((seq, e) =>
      Value(e, arrayValue = Some(ArrayValue(seq.map(t => enc.encode(t, e)))))
    )
  implicit def optionEncoder[T](implicit enc: ValueEncoder[T]) =
    create[Option[T]] {
      case (Some(t), e) => enc.encode(t, e)
      case (None, e)    => Value(e, nullValue = Some("NULL_VALUE"))
    }
}

trait EntityEncoder[A] {
  def encode(obj: A, key: Option[Key], excluded: Set[String]): Entity
}
