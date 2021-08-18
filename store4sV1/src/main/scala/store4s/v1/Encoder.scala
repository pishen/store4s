package store4s.v1

import com.google.datastore.v1.ArrayValue
import com.google.datastore.v1.Entity
import com.google.datastore.v1.Key
import com.google.datastore.v1.Value
import com.google.protobuf.ByteString
import com.google.protobuf.NullValue
import com.google.protobuf.Timestamp
import com.google.`type`.LatLng
import scala.jdk.CollectionConverters._
import shapeless._
import shapeless.labelled._

trait ValueEncoder[T] {
  def encode(t: T): Value.Builder
}

object ValueEncoder {
  def apply[T](implicit enc: ValueEncoder[T]) = enc

  def create[T](f: Value.Builder => T => Value.Builder) = new ValueEncoder[T] {
    def encode(t: T) = f(Value.newBuilder())(t)
  }

  implicit val blobEncoder = create(_.setBlobValue)
  implicit val bytesEncoder =
    create[Array[Byte]](vb =>
      bytes => vb.setBlobValue(ByteString.copyFrom(bytes))
    )
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

trait EntityEncoder[A] {
  def encodeEntity(obj: A, eb: Entity.Builder): Entity.Builder
}

object EntityEncoder {
  def apply[A](implicit enc: EntityEncoder[A]) = enc

  def create[A](f: (A, Entity.Builder) => Entity.Builder) =
    new EntityEncoder[A] {
      def encodeEntity(obj: A, eb: Entity.Builder): Entity.Builder = f(obj, eb)
    }

  implicit val hnilEncoder = create[HNil]((_, eb) => eb)

  implicit def hlistEncoder[K <: Symbol, H, T <: HList](implicit
      witness: Witness.Aux[K],
      hEncoder: ValueEncoder[H],
      tEncoder: EntityEncoder[T]
  ) = create[FieldType[K, H] :: T] { (obj, eb) =>
    val fieldName = witness.value.name
    tEncoder.encodeEntity(
      obj.tail,
      eb.putProperties(fieldName, hEncoder.encode(obj.head).build())
    )
  }

  implicit def genericEncoder[A, R](implicit
      generic: LabelledGeneric.Aux[A, R],
      encoder: EntityEncoder[R]
  ) = create[A]((obj, eb) => encoder.encodeEntity(generic.to(obj), eb))
}
