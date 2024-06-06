package store4s.rpc

import com.google.datastore.v1.entity.Value
import magnolia1._
import java.time.Instant
import com.google.`type`.latlng.LatLng
import com.google.datastore.v1.entity.Key
import com.google.protobuf.struct.NullValue
import com.google.datastore.v1.entity.Entity

case class Decoder[T](
    decode: Value => T,
    // for handling absent field in Deocder[Option[T]]
    handleAbsent: Boolean = false
) {
  def decodeEntity(entity: Entity): T = decode(Value().withEntityValue(entity))

  def map[B](f: T => B): Decoder[B] =
    Decoder[B](decode.andThen(f), handleAbsent)
}

object Decoder {
  type Typeclass[T] = Decoder[T]

  def join[T](ctx: CaseClass[Decoder, T]): Decoder[T] = Decoder[T](v => {
    val props = v.valueType.entityValue.get.properties
    ctx.construct { param =>
      val dec = param.typeclass
      val value = if (dec.handleAbsent) {
        props.getOrElse(
          param.label,
          Value().withNullValue(NullValue.NULL_VALUE)
        )
      } else props(param.label)
      dec.decode(value)
    }
  })

  implicit def gen[T]: Decoder[T] = macro Magnolia.gen[T]

  implicit val boolean: Decoder[Boolean] = Decoder(_.valueType.booleanValue.get)
  implicit val int: Decoder[Int] = Decoder(_.valueType.integerValue.get.toInt)
  implicit val long: Decoder[Long] = Decoder(_.valueType.integerValue.get)
  implicit val double: Decoder[Double] = Decoder(_.valueType.doubleValue.get)
  implicit val string: Decoder[String] = Decoder(_.valueType.stringValue.get)
  implicit val timestamp: Decoder[Instant] = Decoder(
    decode = { v =>
      val timestamp = v.valueType.timestampValue.get
      Instant.ofEpochSecond(timestamp.seconds, timestamp.nanos)
    }
  )
  implicit val key: Decoder[Key] = Decoder(_.valueType.keyValue.get)
  implicit val blob: Decoder[Array[Byte]] = Decoder(
    _.valueType.blobValue.get.toByteArray
  )
  implicit val geoPoint: Decoder[LatLng] = Decoder(
    _.valueType.geoPointValue.get
  )
  implicit def option[T: Decoder]: Decoder[Option[T]] = Decoder(
    decode = { v =>
      if (v.valueType.isNullValue) None
      else Some(implicitly[Decoder[T]].decode(v))
    },
    handleAbsent = true
  )
  implicit def seq[T: Decoder]: Decoder[Seq[T]] = Decoder(
    _.valueType.arrayValue.get.values.map(implicitly[Decoder[T]].decode)
  )
}
