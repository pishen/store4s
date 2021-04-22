package store4s

import com.google.cloud.Timestamp
import com.google.cloud.datastore.{Datastore => _, _}
import com.google.datastore.v1
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

case class EncoderContext(project: String, namespace: Option[String])

trait EntityEncoder[T] extends ValueEncoder[T] {
  def encodeEntity(t: T)(implicit
      ctx: EncoderContext
  ): FullEntity[IncompleteKey]

  def encodeEntity(t: T, keyName: String)(implicit
      ctx: EncoderContext
  ): FullEntity[Key]
}

object EntityEncoder {
  type Typeclass[T] = ValueEncoder[T]

  def apply[T](implicit enc: EntityEncoder[T]) = enc

  def combine[T](ctx: CaseClass[ValueEncoder, T]): EntityEncoder[T] =
    new EntityEncoder[T] {
      def encodeEntity[K <: IncompleteKey](t: T, eb: FullEntity.Builder[K]) = {
        ctx.parameters
          .foldLeft(eb) { (eb, p) =>
            eb.set(p.label, p.typeclass.encode(p.dereference(t)))
          }
          .build()
      }

      def newKeyFactory(encCtx: EncoderContext) = {
        encCtx.namespace
          .map(new KeyFactory(encCtx.project, _))
          .getOrElse(new KeyFactory(encCtx.project))
          .setKind(ctx.typeName.short)
      }

      def encodeEntity(t: T)(implicit encCtx: EncoderContext) = {
        val key = newKeyFactory(encCtx).newKey()
        encodeEntity(t, FullEntity.newBuilder(key))
      }

      def encodeEntity(t: T, keyName: String)(implicit
          encCtx: EncoderContext
      ) = {
        val key = newKeyFactory(encCtx).newKey(keyName)
        encodeEntity(t, FullEntity.newBuilder(key))
      }

      def encode(t: T) = {
        EntityValue.of(encodeEntity(t, FullEntity.newBuilder()))
      }
    }

  implicit def gen[T]: EntityEncoder[T] = macro Magnolia.gen[T]
}

// Datastore V1
trait ValueEncoderV1[T] {
  def encode(t: T): v1.Value
}

object ValueEncoderV1 {
  def apply[T](implicit enc: ValueEncoderV1[T]) = enc

  def create[T](f: v1.Value.Builder => T => v1.Value.Builder) =
    new ValueEncoderV1[T] {
      def encode(t: T) = f(v1.Value.newBuilder)(t).build()
    }

  implicit val blobEncoder =
    create[com.google.protobuf.ByteString](_.setBlobValue)
  implicit val booleanEncoder = create(_.setBooleanValue)
  implicit val doubleEncoder = create(_.setDoubleValue)
  implicit val keyEncoder = create[v1.Key](_.setKeyValue)
  implicit val latLngEncoder =
    create[com.google.`type`.LatLng](_.setGeoPointValue)
  implicit def seqEncoder[T](implicit ve: ValueEncoderV1[T]) =
    create[Seq[T]](b =>
      seq =>
        b.setArrayValue(
          v1.ArrayValue
            .newBuilder()
            .addAllValues(seq.map(t => ve.encode(t)).asJava)
        )
    )
  implicit def optionEncoder[T](implicit ve: ValueEncoderV1[T]) =
    new ValueEncoderV1[Option[T]] {
      def encode(opt: Option[T]) = opt match {
        case Some(t) => ve.encode(t)
        case None =>
          v1.Value
            .newBuilder()
            .setNullValue(com.google.protobuf.NullValue.NULL_VALUE)
            .build()
      }
    }
  implicit val intEncoder = create[Int](b => t => b.setIntegerValue(t.toLong))
  implicit val longEncoder = create(_.setIntegerValue)
  implicit val stringEncoder = create(_.setStringValue)
  implicit val timestampEncoder =
    create[com.google.protobuf.Timestamp](_.setTimestampValue)
}

trait EntityEncoderV1[T] extends ValueEncoderV1[T] {
  def encodeEntity(t: T)(implicit partitionId: v1.PartitionId): v1.Entity

  def encodeEntity(t: T, keyName: String)(implicit
      partitionId: v1.PartitionId
  ): v1.Entity
}

object EntityEncoderV1 {
  type Typeclass[T] = ValueEncoderV1[T]

  def apply[T](implicit enc: EntityEncoderV1[T]) = enc

  def combine[T](ctx: CaseClass[ValueEncoderV1, T]): EntityEncoderV1[T] =
    new EntityEncoderV1[T] {
      def encodeEntity(t: T, key: v1.Key) = {
        val eb = ctx.parameters.foldLeft(v1.Entity.newBuilder().setKey(key)) {
          (eb, p) =>
            eb.putProperties(p.label, p.typeclass.encode(p.dereference(t)))
        }
        eb.build()
      }

      def encodeEntity(t: T)(implicit partitionId: v1.PartitionId) = {
        val path = v1.Key.PathElement
          .newBuilder()
          .setKind(ctx.typeName.short)
          .build()
        val key = v1.Key
          .newBuilder()
          .setPartitionId(partitionId)
          .addPath(path)
          .build()
        encodeEntity(t, key)
      }

      def encodeEntity(t: T, keyName: String)(implicit
          partitionId: v1.PartitionId
      ) = {
        val path = v1.Key.PathElement
          .newBuilder()
          .setKind(ctx.typeName.short)
          .setName(keyName)
          .build()
        val key = v1.Key
          .newBuilder()
          .setPartitionId(partitionId)
          .addPath(path)
          .build()
        encodeEntity(t, key)
      }

      def encode(t: T) = {
        val entity = encodeEntity(t, v1.Key.newBuilder().build())
        v1.Value.newBuilder().setEntityValue(entity).build()
      }
    }

  implicit def gen[T]: EntityEncoderV1[T] = macro Magnolia.gen[T]
}
