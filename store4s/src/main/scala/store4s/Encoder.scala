package store4s

import com.google.cloud.Timestamp
import com.google.cloud.datastore.{Datastore => _, _}
import shapeless._
import shapeless.labelled._

import scala.jdk.CollectionConverters._
import scala.reflect.macros.blackbox.Context

trait ValueEncoder[T] { self =>
  def builder(t: T, excludeFromIndexes: Boolean): ValueBuilder[_, _, _]

  def encode(t: T, excludeFromIndexes: Boolean = false): Value[_] =
    builder(t, excludeFromIndexes).build()

  def contramap[A](f: A => T): ValueEncoder[A] = (a, excludeFromIndexes) =>
    self.builder(f(a), excludeFromIndexes)
}

object ValueEncoder {
  def apply[T](implicit enc: ValueEncoder[T]) = enc

  def create[T](f: T => ValueBuilder[_, _, _]): ValueEncoder[T] =
    (t, excludeFromIndexes) => f(t).setExcludeFromIndexes(excludeFromIndexes)

  implicit val blobEncoder: ValueEncoder[Blob] = create(BlobValue.newBuilder)
  implicit val bytesEncoder: ValueEncoder[Array[Byte]] =
    blobEncoder.contramap[Array[Byte]](Blob.copyFrom)
  implicit val booleanEncoder: ValueEncoder[Boolean] =
    create(BooleanValue.newBuilder)
  implicit val doubleEncoder: ValueEncoder[Double] =
    create(DoubleValue.newBuilder)
  implicit def entityEncoder[T](implicit
      encoder: EntityEncoder[T]
  ): ValueEncoder[T] = create[T] { obj =>
    EntityValue.newBuilder(encoder.encode(obj, None, Set.empty[String]))
  }
  implicit val keyEncoder: ValueEncoder[Key] = create(KeyValue.newBuilder)
  implicit val latLngEncoder: ValueEncoder[LatLng] =
    create(LatLngValue.newBuilder)
  implicit def seqEncoder[T](implicit
      ve: ValueEncoder[T]
  ): ValueEncoder[Seq[T]] = (seq, excludeFromIndexs) => {
    ListValue
      .newBuilder()
      .set(seq.map(t => ve.encode(t, excludeFromIndexs)).asJava)
  }
  implicit def optionEncoder[T](implicit
      ve: ValueEncoder[T]
  ): ValueEncoder[Option[T]] = create[Option[T]] {
    case Some(t) => ve.builder(t, false)
    case None    => NullValue.newBuilder()
  }
  implicit val intEncoder: ValueEncoder[Int] =
    create((i: Int) => LongValue.newBuilder(i.toLong))
  implicit val longEncoder: ValueEncoder[Long] = create(LongValue.newBuilder)
  implicit val stringEncoder: ValueEncoder[String] =
    create(StringValue.newBuilder)
  implicit val timestampEncoder: ValueEncoder[Timestamp] =
    create(TimestampValue.newBuilder)
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

  def excludeFromIndexesUnsafe(properties: String*): EntityEncoder[A] =
    (obj, key, excluded) => self.builder(obj, key, excluded ++ properties.toSet)
}

object EntityEncoder {
  def apply[A](implicit enc: EntityEncoder[A]) = enc

  implicit val hnilEncoder: EntityEncoder[HNil] = (_, key, _) =>
    key match {
      case Some(key) => FullEntity.newBuilder(key)
      case None      => FullEntity.newBuilder()
    }

  implicit def hlistEncoder[K <: Symbol, H, T <: HList](implicit
      witness: Witness.Aux[K],
      hEncoder: ValueEncoder[H],
      tEncoder: EntityEncoder[T]
  ): EntityEncoder[FieldType[K, H] :: T] = (obj, key, excluded) => {
    val fieldName = witness.value.name
    val value = hEncoder.encode(obj.head, excluded.contains(fieldName))
    tEncoder.builder(obj.tail, key, excluded).set(fieldName, value)
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
          .set(ds.typeIdentifier, typeName)
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
