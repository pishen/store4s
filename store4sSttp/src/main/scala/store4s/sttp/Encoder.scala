package store4s.sttp

import shapeless._
import shapeless.labelled._

import java.time.Instant
import java.time.ZoneId
import java.util.Base64
import scala.reflect.macros.blackbox.Context

import model._

trait ValueEncoder[T] { self =>
  def encode(t: T, excludeFromIndexes: Boolean): Value

  def contramap[A](f: A => T): ValueEncoder[A] = (a, excludeFromIndexes) =>
    self.encode(f(a), excludeFromIndexes)
}

object ValueEncoder {
  def apply[T](implicit enc: ValueEncoder[T]) = enc

  implicit val booleanEncoder: ValueEncoder[Boolean] =
    (t, e) => Value(Some(e), booleanValue = Some(t))
  implicit val intEncoder: ValueEncoder[Int] =
    (t, e) => Value(Some(e), integerValue = Some(t.toString))
  implicit val longEncoder: ValueEncoder[Long] =
    (t, e) => Value(Some(e), integerValue = Some(t.toString))
  implicit val doubleEncoder: ValueEncoder[Double] =
    (t, e) => Value(Some(e), doubleValue = Some(t))
  implicit val instantEncoder: ValueEncoder[Instant] = (t, e) =>
    Value(Some(e), timestampValue = Some(t.atZone(ZoneId.of("Z")).toString))
  implicit val keyEncoder: ValueEncoder[Key] =
    (t, e) => Value(Some(e), keyValue = Some(t))
  implicit val stringEncoder: ValueEncoder[String] =
    (t, e) => Value(Some(e), stringValue = Some(t))
  implicit val bytesEncoder: ValueEncoder[Array[Byte]] = (t, e) =>
    Value(Some(e), blobValue = Some(Base64.getEncoder().encodeToString(t)))
  implicit val latLngEncoder: ValueEncoder[LatLng] =
    (t, e) => Value(Some(e), geoPointValue = Some(t))
  implicit def entityEncoder[T](implicit
      enc: EntityEncoder[T]
  ): ValueEncoder[T] =
    (t, e) => Value(Some(e), entityValue = Some(enc.encode(t, None, Set.empty)))
  implicit def seqEncoder[T](implicit
      enc: ValueEncoder[T]
  ): ValueEncoder[Seq[T]] = (seq, e) =>
    Value(
      Some(e),
      arrayValue = Some(ArrayValue(Some(seq.map(t => enc.encode(t, e)))))
    )
  implicit def optionEncoder[T](implicit
      enc: ValueEncoder[T]
  ): ValueEncoder[Option[T]] = {
    case (Some(t), e) => enc.encode(t, e)
    case (None, e)    => Value(Some(e), nullValue = Some("NULL_VALUE"))
  }
}

trait EntityEncoder[A] { self =>
  def encode(obj: A, key: Option[Key], excluded: Set[String]): Entity

  def excludeFromIndexes(selectors: A => Any*): EntityEncoder[A] =
    macro EntityEncoder.excludeFromIndexesImpl[A]

  def excludeFromIndexesUnsafe(properties: String*): EntityEncoder[A] =
    (obj, key, excluded) => self.encode(obj, key, excluded ++ properties.toSet)
}

object EntityEncoder {
  def apply[A](implicit enc: EntityEncoder[A]) = enc

  implicit val hnilEncoder: EntityEncoder[HNil] =
    (_, key, _) => Entity(key, Map.empty[String, Value])

  implicit def hlistEncoder[K <: Symbol, H, T <: HList](implicit
      witness: Witness.Aux[K],
      hEncoder: ValueEncoder[H],
      tEncoder: EntityEncoder[T]
  ): EntityEncoder[FieldType[K, H] :: T] = { (obj, key, excluded) =>
    val fieldName = witness.value.name
    val value = hEncoder.encode(obj.head, excluded.contains(fieldName))
    val entity = tEncoder.encode(obj.tail, key, excluded)
    entity.copy(properties = entity.properties + (fieldName -> value))
  }

  implicit def genericEncoder[A, R](implicit
      generic: LabelledGeneric.Aux[A, R],
      encoder: Lazy[EntityEncoder[R]]
  ): EntityEncoder[A] = (obj, key, excluded) =>
    encoder.value.encode(generic.to(obj), key, excluded)

  implicit val cnilEncoder: EntityEncoder[CNil] = (_, _, _) =>
    throw new Exception("Inconceivable!")

  implicit def coproductEncoder[K <: Symbol, H, T <: Coproduct](implicit
      witness: Witness.Aux[K],
      hEncoder: Lazy[EntityEncoder[H]],
      tEncoder: EntityEncoder[T],
      typeIdentifier: TypeIdentifier
  ): EntityEncoder[FieldType[K, H] :+: T] = { (obj, key, excluded) =>
    val typeName = witness.value.name
    obj match {
      case Inl(h) =>
        val entity = hEncoder.value.encode(h, key, excluded)
        entity.copy(properties =
          entity.properties +
            (typeIdentifier.fieldName -> Value(stringValue = Some(typeName)))
        )
      case Inr(t) => tEncoder.encode(t, key, excluded)
    }
  }

  def excludeFromIndexesImpl[A](c: Context)(selectors: c.Expr[A => Any]*) = {
    import c.universe._

    val names = selectors.map(_.tree).map {
      case q"_.$name" => name.toString()
      case _          => throw new Exception("Only _.field pattern is supported")
    }

    q"""${c.prefix}.excludeFromIndexesUnsafe(..${names})"""
  }
}
