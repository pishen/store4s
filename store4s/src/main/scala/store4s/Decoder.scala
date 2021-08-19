package store4s

import cats.implicits._
import com.google.cloud.Timestamp
import com.google.cloud.datastore.{Datastore => _, _}
import scala.jdk.CollectionConverters._
import scala.util.Try
import shapeless._
import shapeless.labelled._

trait ValueDecoder[T] {
  def decode(v: Value[_]): Either[Throwable, T]
}

object ValueDecoder {
  def apply[T](implicit dec: ValueDecoder[T]) = dec

  def create[V, T](f: V => T) = new ValueDecoder[T] {
    def decode(v: Value[_]) = Try(v.asInstanceOf[V]).toEither.map(f)
  }

  implicit val blobDecoder = create[BlobValue, Blob](_.get)
  implicit val bytesDecoder =
    create[BlobValue, Array[Byte]](_.get().toByteArray())
  implicit val booleanDecoder = create[BooleanValue, Boolean](_.get)
  implicit val doubleDecoder = create[DoubleValue, Double](_.get)
  implicit def entityDecoder[T](implicit decoder: EntityDecoder[T]) =
    new ValueDecoder[T] {
      def decode(v: Value[_]) = Try(v.asInstanceOf[EntityValue]).toEither
        .flatMap(v => decoder.decodeEntity(v.get()))
    }
  implicit val keyDecoder = create[KeyValue, Key](_.get)
  implicit val latLngDecoder = create[LatLngValue, LatLng](_.get)
  implicit def seqDecoder[T](implicit vd: ValueDecoder[T]) =
    new ValueDecoder[Seq[T]] {
      def decode(v: Value[_]) = Try(v.asInstanceOf[ListValue]).toEither
        .flatMap(_.get.asScala.toSeq.map(vd.decode).sequence)
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
  implicit val timestampDecoder = create[TimestampValue, Timestamp](_.get)
}

trait EntityDecoder[A] {
  def decodeEntity(e: FullEntity[_]): Either[Throwable, A]
}

object EntityDecoder {
  def apply[A](implicit dec: EntityDecoder[A]) = dec

  def create[A](f: FullEntity[_] => Either[Throwable, A]) =
    new EntityDecoder[A] {
      def decodeEntity(e: FullEntity[_]) = f(e)
    }

  implicit val hnilDecoder = create[HNil](_ => Right(HNil))

  implicit def hlistDecoder[K <: Symbol, H, T <: HList](implicit
      witness: Witness.Aux[K],
      hDecoder: ValueDecoder[H],
      tDecoder: EntityDecoder[T]
  ) = create[FieldType[K, H] :: T] { e =>
    val fieldName = witness.value.name
    for {
      v <- Try(e.getValue[Value[_]](fieldName)).toEither
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
