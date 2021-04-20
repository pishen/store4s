package scalastore

import com.google.cloud.datastore.{Datastore => _, _}
import magnolia._
import scala.jdk.CollectionConverters._
import scala.language.experimental.macros
import scala.util.Try

trait ValueDecoder[T] {
  def decode(v: Value[_]): Either[Throwable, T]
}

object ValueDecoder {
  def create[T](f: Value[_] => T) = new ValueDecoder[T] {
    def decode(v: Value[_]) = Try(f(v)).toEither
  }

  implicit val blobDecoder = create[Blob](_.asInstanceOf[BlobValue].get)
  implicit val booleanDecoder =
    create[Boolean](_.asInstanceOf[BooleanValue].get)
  implicit val doubleDecoder = create[Double](_.asInstanceOf[DoubleValue].get)
  implicit val keyDecoder = create[Key](_.asInstanceOf[KeyValue].get)
  implicit val latLngDecoder = create[LatLng](_.asInstanceOf[LatLngValue].get)

  implicit val intDecoder = create[Int](_.asInstanceOf[LongValue].get.toInt)
  implicit val longDecoder = create[Long](_.asInstanceOf[LongValue].get)
  implicit val stringDecoder = create(_.asInstanceOf[StringValue].get)
  implicit val timestampDecoder =
    create(_.asInstanceOf[TimestampValue].get.toSqlTimestamp)
}

trait EntityDecoder[T] extends ValueDecoder[T] {
  def decodeEntity(e: FullEntity[_]): Either[Throwable, T]
}

object EntityDecoder {
  type Typeclass[T] = ValueDecoder[T]

  case class DecodeException(errs: List[Throwable]) extends Exception

  def combine[T](ctx: CaseClass[ValueDecoder, T]): EntityDecoder[T] =
    new EntityDecoder[T] {
      def decodeEntity(e: FullEntity[_]) = {
        val valueMap = e.getProperties().asScala
        ctx
          .constructEither { p =>
            valueMap
              .get(p.label)
              .toRight[Throwable](new NoSuchElementException(p.label))
              .flatMap(v => p.typeclass.decode(v))
          }
          .left
          .map(errs => DecodeException(errs))
      }

      def decode(v: Value[_]) = {
        Try(v.asInstanceOf[EntityValue]).toEither.flatMap(ev =>
          decodeEntity(ev.get)
        )
      }
    }

  implicit def gen[T]: EntityDecoder[T] = macro Magnolia.gen[T]
}
