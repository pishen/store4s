package scalastore

import com.google.cloud.Timestamp
import com.google.cloud.datastore.BlobValue
import com.google.cloud.datastore.BooleanValue
import com.google.cloud.datastore.DoubleValue
import com.google.cloud.datastore.EntityValue
import com.google.cloud.datastore.FullEntity
import com.google.cloud.datastore.IncompleteKey
import com.google.cloud.datastore.Key
import com.google.cloud.datastore.KeyValue
import com.google.cloud.datastore.LatLngValue
import com.google.cloud.datastore.ListValue
import com.google.cloud.datastore.LongValue
import com.google.cloud.datastore.StringValue
import com.google.cloud.datastore.TimestampValue
import com.google.cloud.datastore.Value
import magnolia._
import scala.jdk.CollectionConverters._
import scala.language.experimental.macros

trait ValueEncoder[T] {
  type Out
  def encode(t: T): Value[Out]
}

object ValueEncoder {
  def create[T, O](f: T => Value[O]) = new ValueEncoder[T] {
    type Out = O
    def encode(t: T) = f(t)
  }

  implicit val blobEncoder = create(BlobValue.of)
  implicit val booleanEncoder = create(BooleanValue.of)
  implicit val doubleEncoder = create(DoubleValue.of)
  implicit val keyEncoder = create(KeyValue.of)
  implicit val latLngEncoder = create(LatLngValue.of)
  implicit def seqEncoder[T](implicit ve: ValueEncoder[T]) =
    create[Seq[T], java.util.List[_ <: Value[_]]](seq =>
      ListValue.of(seq.map(t => ve.encode(t)).asJava)
    )
  implicit def optionEncoder[T] = ???
  implicit val intEncoder = create((i: Int) => LongValue.of(i.toLong))
  implicit val longEncoder = create(LongValue.of)
  implicit val stringEncoder = create(StringValue.of)
  implicit val timestampEncoder =
    create((t: java.sql.Timestamp) => TimestampValue.of(Timestamp.of(t)))
}

trait EntityEncoder[T] extends ValueEncoder[T] {
  def encodeEntity(t: T)(implicit
      datastore: Datastore
  ): FullEntity[IncompleteKey]

  def encodeEntity(t: T, keyName: String)(implicit
      datastore: Datastore
  ): FullEntity[Key]
}

object EntityEncoder {
  type Typeclass[T] = ValueEncoder[T]

  def combine[T](ctx: CaseClass[ValueEncoder, T]): EntityEncoder[T] =
    new EntityEncoder[T] {
      type Out = FullEntity[_]

      def encodeEntity[K <: IncompleteKey](t: T, z: FullEntity.Builder[K]) = {
        val eb = ctx.parameters.foldLeft(z) { (eb, p) =>
          eb.set(
            p.label,
            p.typeclass.encode(p.dereference(t))
          )
        }
        eb.build()
      }

      def encodeEntity(t: T)(implicit datastore: Datastore) = {
        val key = datastore.keyFactory.setKind(ctx.typeName.short).newKey()
        encodeEntity(t, FullEntity.newBuilder(key))
      }

      def encodeEntity(t: T, keyName: String)(implicit datastore: Datastore) = {
        val key =
          datastore.keyFactory.setKind(ctx.typeName.short).newKey(keyName)
        encodeEntity(t, FullEntity.newBuilder(key))
      }

      def encode(t: T) = {
        EntityValue.of(encodeEntity(t, FullEntity.newBuilder()))
      }
    }

  implicit def gen[T]: EntityEncoder[T] = macro Magnolia.gen[T]
}
