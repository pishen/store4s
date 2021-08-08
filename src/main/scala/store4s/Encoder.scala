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

  // Datastore V1
  def toV1Key(key: IncompleteKey): v1.Key = {
    val par1 = v1.PartitionId
      .newBuilder()
      .setProjectId(key.getProjectId())
    val par2 = Option(key.getNamespace())
      .map(namespace => par1.setNamespaceId(namespace))
      .getOrElse(par1)
    val path1 = v1.Key.PathElement
      .newBuilder()
      .setKind(key.getKind())
    val path2 = key match {
      case k: Key if k.hasId()   => path1.setId(k.getId())
      case k: Key if k.hasName() => path1.setName(k.getName())
      case _                     => path1
    }
    v1.Key
      .newBuilder()
      .setPartitionId(par2)
      .addPath(path2)
      .build()
  }

  def toV1Value(value: Value[_]): v1.Value = {
    import com.google.protobuf.ByteString
    import com.google.`type`.LatLng
    val vb = v1.Value.newBuilder()
    value match {
      case v: BlobValue =>
        vb.setBlobValue(ByteString.copyFrom(v.get().toByteArray())).build()
      case v: BooleanValue => vb.setBooleanValue(v.get()).build()
      case v: DoubleValue  => vb.setDoubleValue(v.get()).build()
      case v: EntityValue =>
        vb.setEntityValue(EntityEncoder.toV1Entity(v.get())).build()
      case v: KeyValue => vb.setKeyValue(toV1Key(v.get())).build()
      case v: LatLngValue =>
        val p = v.get()
        vb.setGeoPointValue(
          LatLng
            .newBuilder()
            .setLatitude(p.getLatitude())
            .setLongitude(p.getLongitude())
        ).build()
      case v: ListValue =>
        vb.setArrayValue(
          v1.ArrayValue
            .newBuilder()
            .addAllValues(v.get().asScala.map(toV1Value).asJava)
        ).build()
      case v: LongValue => vb.setIntegerValue(v.get()).build()
      case _: NullValue =>
        vb.setNullValue(com.google.protobuf.NullValue.NULL_VALUE).build()
      case v: StringValue    => vb.setStringValue(v.get()).build()
      case v: TimestampValue => vb.setTimestampValue(v.get().toProto()).build()
      case v: RawValue       => v.get()
    }
  }

  def toV1Entity(entity: FullEntity[_]): v1.Entity = {
    val keyOpt = Option(entity.getKey())
      .collect { case k: IncompleteKey => k }
      .map(toV1Key)
    val eb = keyOpt match {
      case Some(key) => v1.Entity.newBuilder().setKey(key)
      case None      => v1.Entity.newBuilder()
    }
    entity
      .getProperties()
      .asScala
      .toSeq
      .foldLeft(eb) { case (eb, (name, value)) =>
        eb.putProperties(name, toV1Value(value))
      }
      .build()
  }
}
