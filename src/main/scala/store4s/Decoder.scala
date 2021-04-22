package store4s

import com.google.cloud.datastore.{Datastore => _, _}
import com.google.datastore.v1
import java.sql.Timestamp
import magnolia._
import scala.jdk.CollectionConverters._
import scala.language.experimental.macros
import scala.util.Try

trait ValueDecoder[T] {
  def decode(v: Value[_]): Either[Throwable, T]
}

object ValueDecoder {
  def apply[T](implicit dec: ValueDecoder[T]) = dec

  def create[V, T](f: V => T) = new ValueDecoder[T] {
    def decode(v: Value[_]) = Try(v.asInstanceOf[V]).toEither.map(f)
  }

  implicit val blobDecoder = create[BlobValue, Blob](_.get)
  implicit val booleanDecoder = create[BooleanValue, Boolean](_.get)
  implicit val doubleDecoder = create[DoubleValue, Double](_.get)
  implicit val keyDecoder = create[KeyValue, Key](_.get)
  implicit val latLngDecoder = create[LatLngValue, LatLng](_.get)
  implicit def seqDecoder[T](implicit vd: ValueDecoder[T]) =
    new ValueDecoder[Seq[T]] {
      //TODO: can be simplified by cats Traverse
      def decode(v: Value[_]) = Try(v.asInstanceOf[ListValue]).toEither
        .flatMap { lv =>
          lv.get.asScala.toSeq
            .foldLeft[Either[Throwable, Seq[T]]](Right(Seq.empty[T])) {
              (either, v) =>
                for {
                  seq <- either
                  t <- vd.decode(v)
                } yield {
                  seq :+ t
                }
            }
        }
    }
  implicit def optionDecoder[T](implicit vd: ValueDecoder[T]) =
    new ValueDecoder[Option[T]] {
      def decode(v: Value[_]) = if (v.isInstanceOf[NullValue]) {
        Right(None)
      } else {
        vd.decode(v).map(t => Some(t))
      }
    }
  implicit val intDecoder = create[LongValue, Int](_.get.toInt)
  implicit val longDecoder = create[LongValue, Long](_.get)
  implicit val stringDecoder = create[StringValue, String](_.get)
  implicit val timestampDecoder =
    create[TimestampValue, Timestamp](_.get.toSqlTimestamp)
}

trait EntityDecoder[T] extends ValueDecoder[T] {
  def decodeEntity(e: FullEntity[_]): Either[Throwable, T]
  def decodeV1Entity(e: v1.Entity): Either[Throwable, T]
}

object EntityDecoder {
  type Typeclass[T] = ValueDecoder[T]

  case class DecodeException(errs: List[Throwable]) extends Exception

  def apply[T](implicit dec: EntityDecoder[T]) = dec

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

      def decodeV1Entity(e: v1.Entity) = {
        decodeEntity(FullEntity.fromPb(e))
      }

      def decode(v: Value[_]) = {
        Try(v.asInstanceOf[EntityValue]).toEither.flatMap(ev =>
          decodeEntity(ev.get)
        )
      }
    }

  implicit def gen[T]: EntityDecoder[T] = macro Magnolia.gen[T]
}
