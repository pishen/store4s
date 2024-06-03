package store4s.v1

import com.google.`type`.LatLng
import com.google.datastore.v1.ArrayValue
import com.google.datastore.v1.Entity
import com.google.datastore.v1.Key
import com.google.datastore.v1.Value
import com.google.protobuf.ByteString
import com.google.protobuf.NullValue
import com.google.protobuf.Timestamp
import shapeless._
import shapeless.labelled._

import scala.jdk.CollectionConverters._
import scala.reflect.macros.blackbox.Context

trait ValueEncoder[T] { self =>
  def builder(t: T, excludeFromIndexes: Boolean): Value.Builder

  def encode(t: T, excludeFromIndexes: Boolean = false) =
    builder(t, excludeFromIndexes).build()

  def contramap[A](f: A => T): ValueEncoder[A] = (a, excludeFromIndexes) =>
    self.builder(f(a), excludeFromIndexes)
}

object ValueEncoder {
  def apply[T](implicit enc: ValueEncoder[T]) = enc

  def create[T](f: Value.Builder => T => Value.Builder): ValueEncoder[T] =
    (t, excludeFromIndexes) =>
      f(Value.newBuilder())(t).setExcludeFromIndexes(excludeFromIndexes)

  implicit val blobEncoder: ValueEncoder[ByteString] = create(_.setBlobValue)
  implicit val bytesEncoder: ValueEncoder[Array[Byte]] =
    blobEncoder.contramap[Array[Byte]](ByteString.copyFrom)
  implicit val booleanEncoder: ValueEncoder[Boolean] = create(_.setBooleanValue)
  implicit val doubleEncoder: ValueEncoder[Double] = create(_.setDoubleValue)
  implicit def entityEncoder[T](implicit
      encoder: EntityEncoder[T]
  ): ValueEncoder[T] = create[T](vb =>
    obj => vb.setEntityValue(encoder.builder(obj, None, Set.empty[String]))
  )
  implicit val keyEncoder: ValueEncoder[Key] = create[Key](_.setKeyValue)
  implicit val latLngEncoder: ValueEncoder[LatLng] =
    create[LatLng](vb => latlng => vb.setGeoPointValue(latlng))
  implicit def seqEncoder[T](implicit
      ve: ValueEncoder[T]
  ): ValueEncoder[Seq[T]] = (seq, excludeFromIndexes) =>
    Value
      .newBuilder()
      .setArrayValue(
        ArrayValue
          .newBuilder()
          .addAllValues(
            seq.map(t => ve.encode(t, excludeFromIndexes)).asJava
          )
      )
  implicit def optionEncoder[T](implicit
      ve: ValueEncoder[T]
  ): ValueEncoder[Option[T]] = create[Option[T]](vb => {
    case Some(t) => ve.builder(t, false)
    case None    => vb.setNullValue(NullValue.NULL_VALUE)
  })
  implicit val intEncoder: ValueEncoder[Int] =
    create[Int](vb => i => vb.setIntegerValue(i.toLong))
  implicit val longEncoder: ValueEncoder[Long] = create(_.setIntegerValue)
  implicit val stringEncoder: ValueEncoder[String] = create(_.setStringValue)
  implicit val timestampEncoder: ValueEncoder[Timestamp] =
    create[Timestamp](_.setTimestampValue)
}

trait EntityEncoder[A] { self =>
  def builder(obj: A, key: Option[Key], excluded: Set[String]): Entity.Builder

  def encode(obj: A, key: Option[Key], excluded: Set[String]) =
    builder(obj, key, excluded).build()

  def excludeFromIndexes(selectors: A => Any*): EntityEncoder[A] =
    macro EntityEncoder.excludeFromIndexesImpl[A]

  def excludeFromIndexesUnsafe(properties: String*): EntityEncoder[A] =
    (obj, key, excluded) => self.builder(obj, key, excluded ++ properties.toSet)
}

object EntityEncoder {
  def apply[A](implicit enc: EntityEncoder[A]) = enc

  implicit val hnilEncoder: EntityEncoder[HNil] = {
    case (_, Some(key), _) => Entity.newBuilder().setKey(key)
    case (_, None, _)      => Entity.newBuilder()
  }

  implicit def hlistEncoder[K <: Symbol, H, T <: HList](implicit
      witness: Witness.Aux[K],
      hEncoder: ValueEncoder[H],
      tEncoder: EntityEncoder[T]
  ): EntityEncoder[FieldType[K, H] :: T] = (obj, key, excluded) => {
    val fieldName = witness.value.name
    val value = hEncoder.encode(obj.head, excluded.contains(fieldName))
    tEncoder.builder(obj.tail, key, excluded).putProperties(fieldName, value)
  }

  implicit def genericEncoder[A, R](implicit
      generic: LabelledGeneric.Aux[A, R],
      encoder: Lazy[EntityEncoder[R]]
  ): EntityEncoder[A] = (obj, key, excluded) =>
    encoder.value.builder(generic.to(obj), key, excluded)

  implicit val cnilEncoder: EntityEncoder[CNil] = (_, _, _) =>
    throw new Exception("Inconceivable!")

  implicit def coproductEncoder[K <: Symbol, H, T <: Coproduct](implicit
      witness: Witness.Aux[K],
      hEncoder: Lazy[EntityEncoder[H]],
      tEncoder: EntityEncoder[T],
      ds: Datastore
  ): EntityEncoder[FieldType[K, H] :+: T] = (obj, key, excluded) => {
    val typeName = witness.value.name
    obj match {
      case Inl(h) =>
        hEncoder.value
          .builder(h, key, excluded)
          .putProperties(
            ds.typeIdentifier,
            ValueEncoder[String].encode(typeName)
          )
      case Inr(t) => tEncoder.builder(t, key, excluded)
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
