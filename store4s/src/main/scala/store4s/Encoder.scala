package store4s

import com.google.cloud.datastore.{Datastore => _, _}
import shapeless._
import shapeless.labelled._

import scala.jdk.CollectionConverters._
import scala.language.experimental.macros
import scala.reflect.macros.blackbox.Context

trait ValueEncoder[T] { self =>
  def builder(t: T, excludeFromIndexes: Boolean): ValueBuilder[_, _, _]

  def encode(t: T, excludeFromIndexes: Boolean = false): Value[_] =
    builder(t, excludeFromIndexes).build()

  def contramap[A](f: A => T) = new ValueEncoder[A] {
    def builder(a: A, excludeFromIndexes: Boolean): ValueBuilder[_, _, _] =
      self.builder(f(a), excludeFromIndexes)
  }
}

object ValueEncoder {
  def apply[T](implicit enc: ValueEncoder[T]) = enc

  def create[T](f: T => ValueBuilder[_, _, _]) = new ValueEncoder[T] {
    def builder(t: T, excludeFromIndexes: Boolean): ValueBuilder[_, _, _] =
      f(t).setExcludeFromIndexes(excludeFromIndexes)
  }

  implicit val blobEncoder = create(BlobValue.newBuilder)
  implicit val bytesEncoder = blobEncoder.contramap[Array[Byte]](Blob.copyFrom)
  implicit val booleanEncoder = create(BooleanValue.newBuilder)
  implicit val doubleEncoder = create(DoubleValue.newBuilder)
  implicit def entityEncoder[T](implicit encoder: EntityEncoder[T]) =
    create[T] { obj =>
      EntityValue.newBuilder(encoder.encode(obj, None, Set.empty[String]))
    }
  implicit val keyEncoder = create(KeyValue.newBuilder)
  implicit val latLngEncoder = create(LatLngValue.newBuilder)
  implicit def seqEncoder[T](implicit ve: ValueEncoder[T]) =
    new ValueEncoder[Seq[T]] {
      def builder(
          seq: Seq[T],
          excludeFromIndexs: Boolean
      ): ValueBuilder[_, _, _] = {
        ListValue
          .newBuilder()
          .set(seq.map(t => ve.encode(t, excludeFromIndexs)).asJava)
      }
    }
  implicit def optionEncoder[T](implicit ve: ValueEncoder[T]) =
    create[Option[T]] {
      case Some(t) => ve.builder(t, false)
      case None    => NullValue.newBuilder()
    }
  implicit val intEncoder = create((i: Int) => LongValue.newBuilder(i.toLong))
  implicit val longEncoder = create(LongValue.newBuilder)
  implicit val stringEncoder = create(StringValue.newBuilder)
  implicit val timestampEncoder = create(TimestampValue.newBuilder)
}

trait EntityEncoder[A] { self =>
  def builder(
      obj: A,
      key: Option[IncompleteKey],
      excluded: Set[String]
  ): FullEntity.Builder[IncompleteKey]

  def encode(
      obj: A,
      key: Option[IncompleteKey],
      excluded: Set[String]
  ) = builder(obj, key, excluded).build()

  def excludeFromIndexes(selectors: A => Any*): EntityEncoder[A] =
    macro EntityEncoder.excludeFromIndexesImpl[A]

  def excludeFromIndexesUnsafe(properties: String*) = new EntityEncoder[A] {
    def builder(
        obj: A,
        key: Option[IncompleteKey],
        excluded: Set[String]
    ) = self.builder(obj, key, excluded ++ properties.toSet)
  }
}

object EntityEncoder {
  def apply[A](implicit enc: EntityEncoder[A]) = enc

  implicit val hnilEncoder = new EntityEncoder[HNil] {
    def builder(
        obj: HNil,
        key: Option[IncompleteKey],
        excluded: Set[String]
    ) = key match {
      case Some(key) => FullEntity.newBuilder(key)
      case None      => FullEntity.newBuilder()
    }
  }

  implicit def hlistEncoder[K <: Symbol, H, T <: HList](implicit
      witness: Witness.Aux[K],
      hEncoder: ValueEncoder[H],
      tEncoder: EntityEncoder[T]
  ) = new EntityEncoder[FieldType[K, H] :: T] {
    def builder(
        obj: FieldType[K, H] :: T,
        key: Option[IncompleteKey],
        excluded: Set[String]
    ) = {
      val fieldName = witness.value.name
      val value = hEncoder.encode(obj.head, excluded.contains(fieldName))
      tEncoder.builder(obj.tail, key, excluded).set(fieldName, value)
    }
  }

  implicit def genericEncoder[A, R](implicit
      generic: LabelledGeneric.Aux[A, R],
      encoder: Lazy[EntityEncoder[R]]
  ) = new EntityEncoder[A] {
    def builder(
        obj: A,
        key: Option[IncompleteKey],
        excluded: Set[String]
    ) = encoder.value.builder(generic.to(obj), key, excluded)
  }

  implicit val cnilEncoder = new EntityEncoder[CNil] {
    def builder(
        obj: CNil,
        key: Option[IncompleteKey],
        excluded: Set[String]
    ): FullEntity.Builder[IncompleteKey] = throw new Exception("Inconceivable!")
  }

  implicit def coproductEncoder[K <: Symbol, H, T <: Coproduct](implicit
      witness: Witness.Aux[K],
      hEncoder: Lazy[EntityEncoder[H]],
      tEncoder: EntityEncoder[T],
      ds: Datastore
  ) = new EntityEncoder[FieldType[K, H] :+: T] {
    def builder(
        obj: FieldType[K, H] :+: T,
        key: Option[IncompleteKey],
        excluded: Set[String]
    ) = {
      val typeName = witness.value.name
      obj match {
        case Inl(h) =>
          hEncoder.value
            .builder(h, key, excluded)
            .set(ds.typeIdentifier, typeName)
        case Inr(t) => tEncoder.builder(t, key, excluded)
      }
    }
  }

  def excludeFromIndexesImpl[A](c: Context)(selectors: c.Expr[A => Any]*) = {
    import c.universe._

    val names = selectors.map(_.tree).map { case q"_.$name" =>
      name.toString()
    }

    q"""${c.prefix}.excludeFromIndexesUnsafe(..${names})"""
  }
}
