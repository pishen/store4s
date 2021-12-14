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
  def encode(t: T): Value.Builder

  def contramap[A](f: A => T) = new ValueEncoder[A] {
    def encode(a: A) = self.encode(f(a))
  }
}

object ValueEncoder {
  def apply[T](implicit enc: ValueEncoder[T]) = enc

  def create[T](f: Value.Builder => T => Value.Builder) = new ValueEncoder[T] {
    def encode(t: T) = f(Value.newBuilder())(t)
  }

  implicit val blobEncoder = create(_.setBlobValue)
  implicit val bytesEncoder =
    blobEncoder.contramap[Array[Byte]](ByteString.copyFrom)
  implicit val booleanEncoder = create(_.setBooleanValue)
  implicit val doubleEncoder = create(_.setDoubleValue)
  implicit def entityEncoder[T](implicit encoder: EntityEncoder[T]) =
    create[T](vb =>
      obj => vb.setEntityValue(encoder.encodeEntity(obj, Entity.newBuilder()))
    )
  implicit val keyEncoder = create[Key](_.setKeyValue)
  implicit val latLngEncoder =
    create[LatLng](vb => latlng => vb.setGeoPointValue(latlng))
  implicit def seqEncoder[T](implicit ve: ValueEncoder[T]) =
    create[Seq[T]](vb =>
      seq =>
        vb.setArrayValue(
          ArrayValue
            .newBuilder()
            .addAllValues(seq.map(t => ve.encode(t).build()).asJava)
        )
    )
  implicit def optionEncoder[T](implicit ve: ValueEncoder[T]) =
    create[Option[T]](vb => {
      case Some(t) => ve.encode(t)
      case None    => vb.setNullValue(NullValue.NULL_VALUE)
    })
  implicit val intEncoder = create[Int](vb => i => vb.setIntegerValue(i.toLong))
  implicit val longEncoder = create(_.setIntegerValue)
  implicit val stringEncoder = create(_.setStringValue)
  implicit val timestampEncoder = create[Timestamp](_.setTimestampValue)
}

trait EntityEncoder[A] { self =>
  def encodeEntity(
      obj: A,
      eb: Entity.Builder,
      excluded: Set[String]
  ): Entity.Builder

  def encodeEntity(obj: A, eb: Entity.Builder): Entity.Builder =
    encodeEntity(obj, eb, Set.empty)

  def excludeFromIndexes(properties: String*): EntityEncoder[A] =
    macro EntityEncoder.excludeFromIndexesImpl[A]

  def unsafeExcludeFromIndexes(properties: String*) = new EntityEncoder[A] {
    def encodeEntity(
        obj: A,
        eb: Entity.Builder,
        excluded: Set[String]
    ): Entity.Builder = self.encodeEntity(obj, eb, excluded)
    override def encodeEntity(obj: A, eb: Entity.Builder): Entity.Builder =
      self.encodeEntity(obj, eb, properties.toSet)
  }
}

object EntityEncoder {
  def apply[A](implicit enc: EntityEncoder[A]) = enc

  def create[A](f: (A, Entity.Builder, Set[String]) => Entity.Builder) =
    new EntityEncoder[A] {
      def encodeEntity(
          obj: A,
          eb: Entity.Builder,
          excluded: Set[String]
      ): Entity.Builder = f(obj, eb, excluded)
    }

  implicit val hnilEncoder = create[HNil]((_, eb, _) => eb)

  implicit def hlistEncoder[K <: Symbol, H, T <: HList](implicit
      witness: Witness.Aux[K],
      hEncoder: ValueEncoder[H],
      tEncoder: EntityEncoder[T]
  ) = create[FieldType[K, H] :: T] { (obj, eb, excluded) =>
    val fieldName = witness.value.name
    val value = if (excluded.contains(fieldName)) {
      hEncoder.encode(obj.head).setExcludeFromIndexes(true).build()
    } else {
      hEncoder.encode(obj.head).build()
    }
    tEncoder.encodeEntity(
      obj.tail,
      eb.putProperties(fieldName, value),
      excluded
    )
  }

  implicit def genericEncoder[A, R](implicit
      generic: LabelledGeneric.Aux[A, R],
      encoder: Lazy[EntityEncoder[R]]
  ) = create[A] { (obj, eb, excluded) =>
    encoder.value.encodeEntity(generic.to(obj), eb, excluded)
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
