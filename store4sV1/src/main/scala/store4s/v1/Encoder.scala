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
import scala.language.experimental.macros
import scala.reflect.macros.blackbox.Context

trait ValueEncoder[T] { self =>
  def builder(t: T, excludeFromIndexes: Boolean): Value.Builder

  def encode(t: T, excludeFromIndexes: Boolean = false) =
    builder(t, excludeFromIndexes).build()

  def contramap[A](f: A => T) = new ValueEncoder[A] {
    def builder(a: A, excludeFromIndexes: Boolean) =
      self.builder(f(a), excludeFromIndexes)
  }
}

object ValueEncoder {
  def apply[T](implicit enc: ValueEncoder[T]) = enc

  def create[T](f: Value.Builder => T => Value.Builder) = new ValueEncoder[T] {
    def builder(t: T, excludeFromIndexes: Boolean) =
      f(Value.newBuilder())(t).setExcludeFromIndexes(excludeFromIndexes)
  }

  implicit val blobEncoder = create(_.setBlobValue)
  implicit val bytesEncoder =
    blobEncoder.contramap[Array[Byte]](ByteString.copyFrom)
  implicit val booleanEncoder = create(_.setBooleanValue)
  implicit val doubleEncoder = create(_.setDoubleValue)
  implicit def entityEncoder[T](implicit encoder: EntityEncoder[T]) =
    create[T](vb =>
      obj => vb.setEntityValue(encoder.builder(obj, None, Set.empty[String]))
    )
  implicit val keyEncoder = create[Key](_.setKeyValue)
  implicit val latLngEncoder =
    create[LatLng](vb => latlng => vb.setGeoPointValue(latlng))
  implicit def seqEncoder[T](implicit ve: ValueEncoder[T]) =
    new ValueEncoder[Seq[T]] {
      def builder(seq: Seq[T], excludeFromIndexes: Boolean) = {
        Value
          .newBuilder()
          .setArrayValue(
            ArrayValue
              .newBuilder()
              .addAllValues(
                seq.map(t => ve.encode(t, excludeFromIndexes)).asJava
              )
          )
      }
    }
  implicit def optionEncoder[T](implicit ve: ValueEncoder[T]) =
    create[Option[T]](vb => {
      case Some(t) => ve.builder(t, false)
      case None    => vb.setNullValue(NullValue.NULL_VALUE)
    })
  implicit val intEncoder = create[Int](vb => i => vb.setIntegerValue(i.toLong))
  implicit val longEncoder = create(_.setIntegerValue)
  implicit val stringEncoder = create(_.setStringValue)
  implicit val timestampEncoder = create[Timestamp](_.setTimestampValue)
}

trait EntityEncoder[A] { self =>
  def builder(obj: A, key: Option[Key], excluded: Set[String]): Entity.Builder

  def encode(obj: A, key: Option[Key], excluded: Set[String]) =
    builder(obj, key, excluded).build()

  def excludeFromIndexes(properties: String*): EntityEncoder[A] =
    macro EntityEncoder.excludeFromIndexesImpl[A]

  def unsafeExcludeFromIndexes(properties: String*) = new EntityEncoder[A] {
    def builder(obj: A, key: Option[Key], excluded: Set[String]) =
      self.builder(obj, key, excluded ++ properties.toSet)
  }
}

object EntityEncoder {
  def apply[A](implicit enc: EntityEncoder[A]) = enc

  def create[A](f: (A, Option[Key], Set[String]) => Entity.Builder) =
    new EntityEncoder[A] {
      def builder(obj: A, key: Option[Key], excluded: Set[String]) =
        f(obj, key, excluded)
    }

  implicit val hnilEncoder = create[HNil] {
    case (_, Some(key), _) => Entity.newBuilder().setKey(key)
    case (_, None, _)      => Entity.newBuilder()
  }

  implicit def hlistEncoder[K <: Symbol, H, T <: HList](implicit
      witness: Witness.Aux[K],
      hEncoder: ValueEncoder[H],
      tEncoder: EntityEncoder[T]
  ) = create[FieldType[K, H] :: T] { (obj, key, excluded) =>
    val fieldName = witness.value.name
    val value = hEncoder.encode(obj.head, excluded.contains(fieldName))
    tEncoder.builder(obj.tail, key, excluded).putProperties(fieldName, value)
  }

  implicit def genericEncoder[A, R](implicit
      generic: LabelledGeneric.Aux[A, R],
      encoder: Lazy[EntityEncoder[R]]
  ) = create[A] { (obj, key, excluded) =>
    encoder.value.builder(generic.to(obj), key, excluded)
  }

  implicit val cnilEncoder = create[CNil] { (_, _, _) =>
    throw new Exception("Inconceivable!")
  }

  implicit def coproductEncoder[K <: Symbol, H, T <: Coproduct](implicit
      witness: Witness.Aux[K],
      hEncoder: Lazy[EntityEncoder[H]],
      tEncoder: EntityEncoder[T],
      ds: Datastore
  ) = create[FieldType[K, H] :+: T] { (obj, key, excluded) =>
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

  def excludeFromIndexesImpl[A: c.WeakTypeTag](c: Context)(
      properties: c.Expr[String]*
  ) = {
    import c.universe._

    val typeMembers = weakTypeOf[A].members.collect {
      case m: MethodSymbol if m.isCaseAccessor => m.name.toString()
    }.toSet

    val propertyNames = properties.map(_.tree).map {
      case Literal(Constant(s: String)) => s
      case _ =>
        c.abort(c.enclosingPosition, "properties must be string literals.")
    }

    propertyNames.foreach { p =>
      if (!typeMembers.contains(p))
        c.abort(
          c.enclosingPosition,
          s"${p} is not a member of ${weakTypeOf[A]}"
        )
    }

    q"""${c.prefix}.unsafeExcludeFromIndexes(..${properties})"""
  }
}
