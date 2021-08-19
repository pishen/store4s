package store4s

import com.google.cloud.Timestamp
import com.google.cloud.datastore.{Datastore => _, _}
import scala.jdk.CollectionConverters._
import shapeless._
import shapeless.labelled._

trait ValueEncoder[T] {
  def encode(t: T): Value[_]
}

object ValueEncoder {
  def apply[T](implicit enc: ValueEncoder[T]) = enc

  def create[T](f: T => Value[_]) = new ValueEncoder[T] {
    def encode(t: T) = f(t)
  }

  implicit val blobEncoder = create(BlobValue.of)
  implicit val booleanEncoder = create(BooleanValue.of)
  implicit val doubleEncoder = create(DoubleValue.of)
  implicit def entityEncoder[T](implicit encoder: EntityEncoder[T]) =
    create[T] { obj =>
      EntityValue.of(encoder.encodeEntity(obj, FullEntity.newBuilder()).build())
    }
  implicit val keyEncoder = create(KeyValue.of)
  implicit val latLngEncoder = create(LatLngValue.of)
  implicit def seqEncoder[T](implicit ve: ValueEncoder[T]) =
    create[Seq[T]](seq => ListValue.of(seq.map(t => ve.encode(t)).asJava))
  implicit def optionEncoder[T](implicit ve: ValueEncoder[T]) =
    create[Option[T]] {
      case Some(t) => ve.encode(t)
      case None    => NullValue.of()
    }
  implicit val intEncoder = create((i: Int) => LongValue.of(i.toLong))
  implicit val longEncoder = create(LongValue.of)
  implicit val stringEncoder = create(StringValue.of)
  implicit val timestampEncoder = create(TimestampValue.of)
}

trait EntityEncoder[A] {
  def encodeEntity[B <: BaseEntity.Builder[_, B]](obj: A, eb: B): B
}

object EntityEncoder {
  def apply[A](implicit enc: EntityEncoder[A]) = enc

  implicit val hnilEncoder = new EntityEncoder[HNil] {
    def encodeEntity[B <: BaseEntity.Builder[_, B]](obj: HNil, eb: B): B = eb
  }

  implicit def hlistEncoder[K <: Symbol, H, T <: HList](implicit
      witness: Witness.Aux[K],
      hEncoder: ValueEncoder[H],
      tEncoder: EntityEncoder[T]
  ) = new EntityEncoder[FieldType[K, H] :: T] {
    def encodeEntity[B <: BaseEntity.Builder[_, B]](
        obj: FieldType[K, H] :: T,
        eb: B
    ): B = {
      val fieldName = witness.value.name
      tEncoder.encodeEntity(
        obj.tail,
        eb.set(fieldName, hEncoder.encode(obj.head))
      )
    }
  }

  implicit def genericEncoder[A, R](implicit
      generic: LabelledGeneric.Aux[A, R],
      encoder: EntityEncoder[R]
  ) = new EntityEncoder[A] {
    def encodeEntity[B <: BaseEntity.Builder[_, B]](obj: A, eb: B): B = {
      encoder.encodeEntity(generic.to(obj), eb)
    }
  }
}
