package store4s.v1

import cats.implicits._
import com.google.`type`.LatLng
import com.google.datastore.v1.Entity
import com.google.datastore.v1.Key
import com.google.datastore.v1.Value
import com.google.protobuf.ByteString
import com.google.protobuf.NullValue
import com.google.protobuf.Timestamp
import shapeless._
import shapeless.labelled._

import scala.jdk.CollectionConverters._
import scala.util.Try

trait ValueDecoder[T] { self =>
  def decode(v: Value): Either[Throwable, T]

  def map[B](f: T => B): ValueDecoder[B] = v => self.decode(v).map(f)

  def emap[B](f: T => Either[Throwable, B]): ValueDecoder[B] =
    v => self.decode(v).flatMap(f)

  /** Return `true` for `ValueDecoder[Option[T]]` */
  def acceptOption = false
}

object ValueDecoder {
  def apply[T](implicit dec: ValueDecoder[T]) = dec

  def create[T](
      checker: Value => Boolean
  )(getter: Value => T): ValueDecoder[T] = v =>
    if (checker(v)) {
      Right(getter(v))
    } else {
      Left(new Exception("Type is not matched: " + v))
    }

  implicit val blobDecoder: ValueDecoder[ByteString] =
    create(_.hasBlobValue())(_.getBlobValue())
  implicit val bytesDecoder: ValueDecoder[Array[Byte]] =
    blobDecoder.map(_.toByteArray())
  implicit val booleanDecoder: ValueDecoder[Boolean] =
    create(_.hasBooleanValue())(_.getBooleanValue())
  implicit val doubleDecoder: ValueDecoder[Double] =
    create(_.hasDoubleValue())(_.getDoubleValue())
  implicit def entityDecoder[T](implicit
      decoder: EntityDecoder[T]
  ): ValueDecoder[T] = v =>
    if (v.hasEntityValue()) {
      Right(v).flatMap(v => decoder.decode(v.getEntityValue()))
    } else {
      Left(new Exception("Type is not matched: " + v))
    }
  implicit val keyDecoder: ValueDecoder[Key] =
    create(_.hasKeyValue())(_.getKeyValue())
  implicit val latLngDecoder: ValueDecoder[LatLng] =
    create(_.hasGeoPointValue())(_.getGeoPointValue())
  implicit def seqDecoder[T](implicit
      vd: ValueDecoder[T]
  ): ValueDecoder[Seq[T]] = v =>
    if (v.hasArrayValue()) {
      v.getArrayValue().getValuesList().asScala.toList.traverse(vd.decode)
    } else {
      Left(new Exception("Type is not matched: " + v))
    }
  implicit def optionDecoder[T](implicit
      vd: ValueDecoder[T]
  ): ValueDecoder[Option[T]] = new ValueDecoder[Option[T]] {
    def decode(v: Value) = if (v.hasNullValue()) {
      Right(None)
    } else {
      vd.decode(v).map(t => Some(t))
    }
    override def acceptOption = true
  }
  implicit val intDecoder: ValueDecoder[Int] =
    create(_.hasIntegerValue())(_.getIntegerValue().toInt)
  implicit val longDecoder: ValueDecoder[Long] =
    create(_.hasIntegerValue())(_.getIntegerValue())
  implicit val stringDecoder: ValueDecoder[String] =
    create(_.hasStringValue())(_.getStringValue())
  implicit val timestampDecoder: ValueDecoder[Timestamp] =
    create(_.hasTimestampValue())(_.getTimestampValue())
}

trait EntityDecoder[A] {
  def decode(e: Entity): Either[Throwable, A]
}

object EntityDecoder {
  def apply[A](implicit dec: EntityDecoder[A]) = dec

  implicit val hnilDecoder: EntityDecoder[HNil] = _ => Right(HNil)

  implicit def hlistDecoder[K <: Symbol, H, T <: HList](implicit
      witness: Witness.Aux[K],
      hDecoder: ValueDecoder[H],
      tDecoder: EntityDecoder[T]
  ): EntityDecoder[FieldType[K, H] :: T] = e => {
    val fieldName = witness.value.name
    for {
      v <-
        if (e.containsProperties(fieldName)) {
          Right(e.getPropertiesOrThrow(fieldName))
        } else if (hDecoder.acceptOption) {
          Right(Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build())
        } else {
          Left(new IllegalArgumentException("Property not found: " + fieldName))
        }
      h <- hDecoder.decode(v)
      t <- tDecoder.decode(e)
    } yield {
      field[K](h) :: t
    }
  }

  implicit def genericDecoder[A, R](implicit
      generic: LabelledGeneric.Aux[A, R],
      decoder: Lazy[EntityDecoder[R]]
  ): EntityDecoder[A] = e => decoder.value.decode(e).map(generic.from)

  implicit val cnilDecoder: EntityDecoder[CNil] =
    e => throw new Exception("No matching type for " + e)

  implicit def coproductDecoder[K <: Symbol, H, T <: Coproduct](implicit
      witness: Witness.Aux[K],
      hDecoder: Lazy[EntityDecoder[H]],
      tDecoder: EntityDecoder[T],
      ds: Datastore
  ): EntityDecoder[FieldType[K, H] :+: T] = e => {
    val typeName = witness.value.name
    Try(e.getPropertiesOrThrow(ds.typeIdentifier).getStringValue()).toEither
      .flatMap { name =>
        if (name == typeName) {
          hDecoder.value.decode(e).map(h => Inl(field[K](h)))
        } else {
          tDecoder.decode(e).map(t => Inr(t))
        }
      }
  }
}
