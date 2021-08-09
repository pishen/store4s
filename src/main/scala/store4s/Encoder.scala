package store4s

import com.google.cloud.Timestamp
import com.google.cloud.datastore.{Datastore => _, _}
import magnolia._
import scala.jdk.CollectionConverters._
import scala.language.experimental.macros

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
  implicit val timestampEncoder =
    create((t: java.sql.Timestamp) => TimestampValue.of(Timestamp.of(t)))
}

trait EntityEncoder[T] extends ValueEncoder[T] {
  def encodeEntity(t: T)(implicit ctx: KeyContext): FullEntity[IncompleteKey]

  def encodeEntity(t: T, keyName: String)(implicit
      ctx: KeyContext
  ): Entity

  def encodeEntity(t: T, id: Long)(implicit ctx: KeyContext): Entity
}

object EntityEncoder {
  type Typeclass[T] = ValueEncoder[T]

  def apply[T](implicit enc: EntityEncoder[T]) = enc

  def combine[T](ctx: CaseClass[ValueEncoder, T]): EntityEncoder[T] =
    new EntityEncoder[T] {
      def fold[B <: BaseEntity.Builder[_, B]](t: T, eb: B) = {
        ctx.parameters
          .foldLeft(eb) { (eb, p) =>
            eb.set(p.label, p.typeclass.encode(p.dereference(t)))
          }
      }

      def encodeEntity(t: T)(implicit keyCtx: KeyContext) = {
        val key = keyCtx.newKeyFactory(ctx.typeName.short).newKey()
        fold(t, FullEntity.newBuilder(key)).build()
      }

      def encodeEntity(t: T, keyName: String)(implicit keyCtx: KeyContext) = {
        val key = keyCtx.newKeyFactory(ctx.typeName.short).newKey(keyName)
        fold(t, Entity.newBuilder(key)).build()
      }

      def encodeEntity(t: T, id: Long)(implicit keyCtx: KeyContext) = {
        val key = keyCtx.newKeyFactory(ctx.typeName.short).newKey(id)
        fold(t, Entity.newBuilder(key)).build()
      }

      def encode(t: T) = {
        EntityValue.of(fold(t, FullEntity.newBuilder()).build())
      }
    }

  implicit def gen[T]: EntityEncoder[T] = macro Magnolia.gen[T]
}
