package store4s.sttp

import cats.syntax.all._
import shapeless._
import shapeless.labelled._

import java.time.Instant
import java.util.Base64

import model._

case class ValueDecodeError(targetType: String)
    extends Exception(s"Cannot decode Value to ${targetType}")
case class EntityDecodeError(fieldName: String)
    extends Exception(s"Property not found: ${fieldName}")

trait ValueDecoder[T] { self =>
  def decode(v: Value): Either[Exception, T]

  def map[B](f: T => B): ValueDecoder[B] = v => self.decode(v).map(f)

  def emap[B](f: T => Either[Exception, B]): ValueDecoder[B] =
    v => self.decode(v).flatMap(f)

  /** Return `true` for `ValueDecoder[Option[T]]` */
  def acceptOption = false
}

object ValueDecoder {
  def apply[T](implicit dec: ValueDecoder[T]) = dec

  def decodeError(targetType: String) = ValueDecodeError(targetType)

  implicit val booleanDecoder: ValueDecoder[Boolean] =
    _.booleanValue.toRight(decodeError("Boolean"))
  implicit val intDecoder: ValueDecoder[Int] =
    _.integerValue.map(_.toInt).toRight(decodeError("Int"))
  implicit val longDecoder: ValueDecoder[Long] =
    _.integerValue.map(_.toLong).toRight(decodeError("Long"))
  implicit val doubleDecoder: ValueDecoder[Double] =
    _.doubleValue.toRight(decodeError("Double"))
  implicit val instantDecoder: ValueDecoder[Instant] =
    _.timestampValue.map(Instant.parse).toRight(decodeError("Instant"))
  implicit val keyDecoder: ValueDecoder[Key] =
    _.keyValue.toRight(decodeError("Key"))
  implicit val stringDecoder: ValueDecoder[String] =
    _.stringValue.toRight(decodeError("String"))
  implicit val bytesDecoder: ValueDecoder[Array[Byte]] =
    _.blobValue
      .map(Base64.getDecoder().decode)
      .toRight(decodeError("Array[Byte]"))
  implicit val latLngDecoder: ValueDecoder[LatLng] =
    _.geoPointValue.toRight(decodeError("LatLng"))
  implicit def entityDecoder[T](implicit
      dec: EntityDecoder[T]
  ): ValueDecoder[T] =
    _.entityValue.toRight(decodeError("Entity")).flatMap(dec.decode)
  implicit def seqDecoder[T](implicit
      dec: ValueDecoder[T]
  ): ValueDecoder[Seq[T]] =
    _.arrayValue
      .toRight(decodeError("Seq"))
      .flatMap(_.values.getOrElse(Seq.empty).toList.traverse(dec.decode))
  implicit def optionDecoder[T](implicit
      dec: ValueDecoder[T]
  ): ValueDecoder[Option[T]] = new ValueDecoder[Option[T]] {
    // Datastore returns null Value as { "nullValue": null } but not { "nullValue": "NULL_VALUE" }
    def decode(v: Value) = {
      if (v.copy(excludeFromIndexes = None, nullValue = None) == Value()) {
        Right(None)
      } else {
        dec.decode(v).map(t => Some(t))
      }
    }
    override def acceptOption = true
  }
}

trait EntityDecoder[A] {
  def decode(e: Entity): Either[Exception, A]
}

object EntityDecoder {
  def apply[A](implicit dec: EntityDecoder[A]) = dec

  implicit val hnilDecoder: EntityDecoder[HNil] = _ => Right(HNil)

  implicit def hlistDecoder[K <: Symbol, H, T <: HList](implicit
      witness: Witness.Aux[K],
      hDecoder: ValueDecoder[H],
      tDecoder: EntityDecoder[T]
  ): EntityDecoder[FieldType[K, H] :: T] = { e =>
    val fieldName = witness.value.name
    for {
      v <- e.properties
        .get(fieldName)
        .orElse(Some(Value()).filter(_ => hDecoder.acceptOption))
        .toRight(EntityDecodeError(fieldName))
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

  implicit val cnilDecoder: EntityDecoder[shapeless.CNil] = _ =>
    Left(new Exception("Not a subtype of the trait"))

  implicit def coproductDecoder[K <: Symbol, H, T <: Coproduct](implicit
      witness: Witness.Aux[K],
      hDecoder: Lazy[EntityDecoder[H]],
      tDecoder: EntityDecoder[T],
      typeIdentifier: TypeIdentifier
  ): EntityDecoder[FieldType[K, H] :+: T] = { e =>
    val typeName = witness.value.name
    e.properties
      .get(typeIdentifier.fieldName)
      .flatMap(_.stringValue)
      .toRight(new Exception("typeIdentifier not found"))
      .flatMap { name =>
        if (name == typeName) {
          hDecoder.value.decode(e).map(h => Inl(field[K](h)))
        } else {
          tDecoder.decode(e).map(t => Inr(t))
        }
      }
  }
}
