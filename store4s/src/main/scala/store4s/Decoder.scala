package store4s

import cats.implicits._
import com.google.cloud.datastore.{Datastore => _, _}
import shapeless._
import shapeless.labelled._

import scala.jdk.CollectionConverters._
import scala.util.Try

trait ValueDecoder[T] { self =>
  def decode(v: Value[_]): Either[Throwable, T]

  def map[B](f: T => B) = new ValueDecoder[B] {
    def decode(v: Value[_]) = self.decode(v).map(f)
  }

  def emap[B](f: T => Either[Throwable, B]) = new ValueDecoder[B] {
    def decode(v: Value[_]) = self.decode(v).flatMap(f)
  }

  /** Return `true` for `ValueDecoder[Option[T]]` */
  def acceptOption = false
}

object ValueDecoder {
  def apply[T](implicit dec: ValueDecoder[T]) = dec

  def create[T](f: Value[_] => T) = new ValueDecoder[T] {
    def decode(v: Value[_]) = Try(f(v)).toEither
  }

  implicit val blobDecoder = create(_.asInstanceOf[BlobValue].get())
  implicit val bytesDecoder = blobDecoder.map(_.toByteArray())
  implicit val booleanDecoder =
    create[Boolean](_.asInstanceOf[BooleanValue].get())
  implicit val doubleDecoder = create(_.asInstanceOf[DoubleValue].get())
  implicit def entityDecoder[T](implicit decoder: EntityDecoder[T]) =
    new ValueDecoder[T] {
      def decode(v: Value[_]) = Try(v.asInstanceOf[EntityValue]).toEither
        .flatMap(v => decoder.decodeEntity(v.get()))
    }
  implicit val keyDecoder = create(_.asInstanceOf[KeyValue].get())
  implicit val latLngDecoder = create(_.asInstanceOf[LatLngValue].get())
  implicit def seqDecoder[T](implicit vd: ValueDecoder[T]) =
    new ValueDecoder[Seq[T]] {
      def decode(v: Value[_]): Either[Throwable, Seq[T]] =
        Try(v.asInstanceOf[ListValue]).toEither
          .flatMap(_.get.asScala.toList.traverse(vd.decode))
    }
  implicit def optionDecoder[T](implicit vd: ValueDecoder[T]) =
    new ValueDecoder[Option[T]] {
      def decode(v: Value[_]) = if (v.isInstanceOf[NullValue]) {
        Right(None)
      } else {
        vd.decode(v).map(t => Some(t))
      }
      override def acceptOption = true
    }
  implicit val intDecoder = create(_.asInstanceOf[LongValue].get().toInt)
  implicit val longDecoder = create[Long](_.asInstanceOf[LongValue].get())
  implicit val stringDecoder = create(_.asInstanceOf[StringValue].get())
  implicit val timestampDecoder = create(_.asInstanceOf[TimestampValue].get())
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
      v <-
        if (e.contains(fieldName)) {
          Right(e.getValue[Value[_]](fieldName))
        } else if (hDecoder.acceptOption) {
          Right(NullValue.of())
        } else {
          Try(e.getValue[Value[_]](fieldName)).toEither
        }
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
