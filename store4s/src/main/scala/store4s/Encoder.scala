package store4s

import com.google.cloud.datastore.{Datastore => _, _}
import shapeless._
import shapeless.labelled._

import scala.jdk.CollectionConverters._
import scala.language.existentials
import scala.language.experimental.macros
import scala.reflect.macros.blackbox.Context

trait ValueEncoder[T] { self =>
  def builder(t: T): ValueBuilder[_, _, _]

  def encode(t: T): Value[_] = builder(t).build()

  def contramap[A](f: A => T) = new ValueEncoder[A] {
    def builder(a: A) = self.builder(f(a))
  }
}

object ValueEncoder {
  def apply[T](implicit enc: ValueEncoder[T]) = enc

  def create[T](f: T => ValueBuilder[_, _, _]) = new ValueEncoder[T] {
    def builder(t: T) = f(t)
  }

  implicit val blobEncoder = create(BlobValue.newBuilder)
  implicit val bytesEncoder = blobEncoder.contramap[Array[Byte]](Blob.copyFrom)
  implicit val booleanEncoder = create(BooleanValue.newBuilder)
  implicit val doubleEncoder = create(DoubleValue.newBuilder)
  implicit def entityEncoder[T](implicit encoder: EntityEncoder[T]) =
    create[T] { obj =>
      EntityValue.newBuilder(
        encoder.encodeEntity(obj, FullEntity.newBuilder()).build()
      )
    }
  implicit val keyEncoder = create(KeyValue.newBuilder)
  implicit val latLngEncoder = create(LatLngValue.newBuilder)
  implicit def seqEncoder[T](implicit ve: ValueEncoder[T]) =
    create[Seq[T]](seq =>
      ListValue.newBuilder().set(seq.map(t => ve.encode(t)).asJava)
    )
  implicit def optionEncoder[T](implicit ve: ValueEncoder[T]) =
    create[Option[T]] {
      case Some(t) => ve.builder(t)
      case None    => NullValue.newBuilder()
    }
  implicit val intEncoder = create((i: Int) => LongValue.newBuilder(i.toLong))
  implicit val longEncoder = create(LongValue.newBuilder)
  implicit val stringEncoder = create(StringValue.newBuilder)
  implicit val timestampEncoder = create(TimestampValue.newBuilder)
}

trait EntityEncoder[A] { self =>
  def encodeEntity[B <: BaseEntity.Builder[_, B]](
      obj: A,
      eb: B,
      excluded: Set[String]
  ): B

  def encodeEntity[B <: BaseEntity.Builder[_, B]](obj: A, eb: B): B =
    encodeEntity(obj, eb, Set.empty)

  def excludeFromIndexes(properties: String*): EntityEncoder[A] =
    macro EntityEncoder.excludeFromIndexesImpl[A]

  def unsafeExcludeFromIndexes(properties: String*) = new EntityEncoder[A] {
    def encodeEntity[B <: BaseEntity.Builder[_, B]](
        obj: A,
        eb: B,
        excluded: Set[String]
    ): B = self.encodeEntity(obj, eb, excluded)
    override def encodeEntity[B <: BaseEntity.Builder[_, B]](obj: A, eb: B): B =
      self.encodeEntity(obj, eb, properties.toSet)
  }
}

object EntityEncoder {
  def apply[A](implicit enc: EntityEncoder[A]) = enc

  implicit val hnilEncoder = new EntityEncoder[HNil] {
    def encodeEntity[B <: BaseEntity.Builder[_, B]](
        obj: HNil,
        eb: B,
        excluded: Set[String]
    ): B = eb
  }

  implicit def hlistEncoder[K <: Symbol, H, T <: HList](implicit
      witness: Witness.Aux[K],
      hEncoder: ValueEncoder[H],
      tEncoder: EntityEncoder[T]
  ) = new EntityEncoder[FieldType[K, H] :: T] {
    def encodeEntity[B <: BaseEntity.Builder[_, B]](
        obj: FieldType[K, H] :: T,
        eb: B,
        excluded: Set[String]
    ): B = {
      val fieldName = witness.value.name
      val value = if (excluded.contains(fieldName)) {
        hEncoder.builder(obj.head).setExcludeFromIndexes(true).build()
      } else {
        hEncoder.encode(obj.head)
      }
      tEncoder.encodeEntity(obj.tail, eb.set(fieldName, value), excluded)
    }
  }

  implicit def genericEncoder[A, R](implicit
      generic: LabelledGeneric.Aux[A, R],
      encoder: Lazy[EntityEncoder[R]]
  ) = new EntityEncoder[A] {
    def encodeEntity[B <: BaseEntity.Builder[_, B]](
        obj: A,
        eb: B,
        excluded: Set[String]
    ): B = {
      encoder.value.encodeEntity(generic.to(obj), eb, excluded)
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
