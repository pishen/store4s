package store4s.v1

import cats.implicits._
import com.google.datastore.v1.Entity
import com.google.datastore.v1.Value
import scala.jdk.CollectionConverters._
import scala.util.Try
import shapeless._
import shapeless.labelled._

trait ValueDecoder[T] {
  def decode(v: Value): Either[Throwable, T]
}

object ValueDecoder {
  def apply[T](implicit dec: ValueDecoder[T]) = dec

  def create[T](checker: Value => Boolean)(getter: Value => T) =
    new ValueDecoder[T] {
      def decode(v: Value) = if (checker(v)) {
        Right(getter(v))
      } else {
        Left(new Exception("Type is not matched: " + v))
      }
    }

  implicit val blobDecoder = create(_.hasBlobValue())(_.getBlobValue())
  implicit val booleanDecoder = create(_.hasBooleanValue())(_.getBooleanValue())
  implicit val doubleDecoder = create(_.hasDoubleValue())(_.getDoubleValue())
  implicit def entityDecoder[T](implicit decoder: EntityDecoder[T]) =
    new ValueDecoder[T] {
      def decode(v: Value) = if (v.hasEntityValue()) {
        Right(v).flatMap(v => decoder.decodeEntity(v.getEntityValue()))
      } else {
        Left(new Exception("Type is not matched: " + v))
      }
    }
  implicit val keyDecoder = create(_.hasKeyValue())(_.getKeyValue())
  implicit val latLngDecoder =
    create(_.hasGeoPointValue())(_.getGeoPointValue())
  implicit def seqDecoder[T](implicit vd: ValueDecoder[T]) =
    new ValueDecoder[Seq[T]] {
      def decode(v: Value): Either[Throwable, Seq[T]] = if (v.hasArrayValue()) {
        v.getArrayValue().getValuesList().asScala.toList.traverse(vd.decode)
      } else {
        Left(new Exception("Type is not matched: " + v))
      }
    }
  implicit def optionDecoder[T](implicit vd: ValueDecoder[T]) =
    new ValueDecoder[Option[T]] {
      def decode(v: Value) = if (v.hasNullValue()) {
        Right(None)
      } else {
        vd.decode(v).map(t => Some(t))
      }
    }
  implicit val intDecoder =
    create(_.hasIntegerValue())(_.getIntegerValue().toInt)
  implicit val longDecoder = create(_.hasIntegerValue())(_.getIntegerValue())
  implicit val stringDecoder = create(_.hasStringValue())(_.getStringValue())
  implicit val timestampDecoder =
    create(_.hasTimestampValue())(_.getTimestampValue())
}

trait EntityDecoder[A] {
  def decodeEntity(e: Entity): Either[Throwable, A]
}

object EntityDecoder {
  def apply[A](implicit dec: EntityDecoder[A]) = dec

  def create[A](f: Entity => Either[Throwable, A]) = new EntityDecoder[A] {
    def decodeEntity(e: Entity) = f(e)
  }

  implicit val hnilDecoder = create[HNil](_ => Right(HNil))

  implicit def hlistDecoder[K <: Symbol, H, T <: HList](implicit
      witness: Witness.Aux[K],
      hDecoder: ValueDecoder[H],
      tDecoder: EntityDecoder[T]
  ) = create[FieldType[K, H] :: T] { e =>
    val fieldName = witness.value.name
    for {
      v <- Try(e.getPropertiesOrThrow(fieldName)).toEither
      h <- hDecoder.decode(v)
      t <- tDecoder.decodeEntity(e)
    } yield {
      field[K](h) :: t
    }
  }

  implicit def genericDecoder[A, R](implicit
      generic: LabelledGeneric.Aux[A, R],
      decoder: EntityDecoder[R]
  ) = create[A] { e =>
    decoder.decodeEntity(e).map(generic.from)
  }
}
