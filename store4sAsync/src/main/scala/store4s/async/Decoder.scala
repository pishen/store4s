package store4s.async

import cats.syntax.all._
import shapeless._
import shapeless.labelled._

import java.time.Instant
import java.util.Base64

import model._

trait ValueDecoder[T] { self =>
  def decode(v: Value): Either[Exception, T]

  def map[B](f: T => B) = new ValueDecoder[B] {
    def decode(v: Value) = self.decode(v).map(f)
  }

  def emap[B](f: T => Either[Exception, B]) = new ValueDecoder[B] {
    def decode(v: Value) = self.decode(v).flatMap(f)
  }

  /** Return `true` for `ValueDecoder[Option[T]]` */
  def acceptOption = false
}

object ValueDecoder {
  def apply[T](implicit dec: ValueDecoder[T]) = dec

  def decodeError(targetType: String) = new Exception(
    s"Cannot decode Value to ${targetType}"
  )

  def create[T](f: Value => Either[Exception, T]) =
    new ValueDecoder[T] {
      def decode(v: Value) = f(v)
    }

  implicit val booleanDecoder =
    create[Boolean](_.booleanValue.toRight(decodeError("Boolean")))
  implicit val intDecoder =
    create[Int](_.integerValue.map(_.toInt).toRight(decodeError("Int")))
  implicit val longDecoder =
    create[Long](_.integerValue.map(_.toLong).toRight(decodeError("Long")))
  implicit val doubleDecoder =
    create[Double](_.doubleValue.toRight(decodeError("Double")))
  implicit val instantDecoder =
    create[Instant](
      _.timestampValue.map(Instant.parse).toRight(decodeError("Instant"))
    )
  implicit val keyDecoder =
    create[Key](_.keyValue.toRight(decodeError("Key")))
  implicit val stringDecoder =
    create[String](_.stringValue.toRight(decodeError("String")))
  implicit val bytesDecoder =
    create[Array[Byte]](
      _.blobValue
        .map(Base64.getDecoder().decode)
        .toRight(decodeError("Array[Byte]"))
    )
  implicit val latLngDecoder =
    create[LatLng](_.geoPointValue.toRight(decodeError("LatLng")))
  implicit def entityDecoder[T](implicit dec: EntityDecoder[T]) =
    create[T](_.entityValue.toRight(decodeError("Entity")).flatMap(dec.decode))
  implicit def seqDecoder[T](implicit dec: ValueDecoder[T]) =
    create[Seq[T]](
      _.arrayValue
        .toRight(decodeError("Seq"))
        .flatMap(_.values.toList.traverse(dec.decode))
    )
  implicit def optionDecoder[T](implicit dec: ValueDecoder[T]) =
    new ValueDecoder[Option[T]] {
      def decode(v: Value) = if (v.nullValue.nonEmpty) {
        Right(None)
      } else {
        dec.decode(v).map(t => Some(t))
      }
      override def acceptOption = true
    }
}

trait EntityDecoder[A] {
  def decode(e: Entity): Either[Exception, A]
}

object EntityDecoder {
  def apply[A](implicit dec: EntityDecoder[A]) = dec

  def create[A](f: Entity => Either[Exception, A]) =
    new EntityDecoder[A] {
      def decode(e: Entity) = f(e)
    }

  implicit val hnilDecoder = create[HNil](_ => Right(HNil))

  implicit def hlistDecoder[K <: Symbol, H, T <: HList](implicit
      witness: Witness.Aux[K],
      hDecoder: ValueDecoder[H],
      tDecoder: EntityDecoder[T]
  ) = create[FieldType[K, H] :: T] { e =>
    val fieldName = witness.value.name
    for {
      v <- e.properties
        .get(fieldName)
        .orElse(
          Some(Value(false, nullValue = Some("NULL_VALUE")))
            .filter(_ => hDecoder.acceptOption)
        )
        .toRight(new Exception(s"Property not found: ${fieldName}"))
      h <- hDecoder.decode(v)
      t <- tDecoder.decode(e)
    } yield {
      field[K](h) :: t
    }
  }

  implicit def genericDecoder[A, R](implicit
      generic: LabelledGeneric.Aux[A, R],
      decoder: Lazy[EntityDecoder[R]]
  ) = create[A] { e =>
    decoder.value.decode(e).map(generic.from)
  }

  implicit val cnilDecoder = create[CNil] { _ =>
    Left(new Exception("Not a subtype of the trait"))
  }

  implicit def coproductDecoder[K <: Symbol, H, T <: Coproduct](implicit
      witness: Witness.Aux[K],
      hDecoder: Lazy[EntityDecoder[H]],
      tDecoder: EntityDecoder[T],
      typeIdentifier: TypeIdentifier
  ) = create[FieldType[K, H] :+: T] { e =>
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
