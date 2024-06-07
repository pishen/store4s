package store4s.rpc

import com.google.`type`.latlng.LatLng
import com.google.datastore.v1.entity.ArrayValue
import com.google.datastore.v1.entity.Entity
import com.google.datastore.v1.entity.Key
import com.google.datastore.v1.entity.Key.PathElement
import com.google.datastore.v1.entity.Key.PathElement.IdType
import com.google.datastore.v1.entity.PartitionId
import com.google.datastore.v1.entity.Value
import com.google.protobuf.ByteString
import com.google.protobuf.struct.NullValue
import com.google.protobuf.timestamp.Timestamp
import magnolia1._

import java.time.Instant
import scala.reflect.macros.blackbox.Context

case class Encoder[T](
    base: T => Value,
    kind: String = "",
    idType: T => IdType = (_: T) => IdType.Empty,
    projectId: String = Datastore.defaultProjectId,
    databaseId: String = "",
    namespaceId: String = "",
    excludedFields: Set[String] = Set.empty
) {
  def encode(t: T): Value = {
    val value = base(t)
    if (value.valueType.isEntityValue) {
      val entity = value.getEntityValue
      val newProperties = entity.properties.map { case (k, v) =>
        if (excludedFields.contains(k)) {
          if (v.valueType.isArrayValue) {
            val newValues =
              v.getArrayValue.values.map(_.withExcludeFromIndexes(true))
            k -> v.withArrayValue(ArrayValue(values = newValues))
          } else k -> v.withExcludeFromIndexes(true)
        } else k -> v
      }
      value.withEntityValue(entity.withProperties(newProperties))
    } else value
  }

  def encodeEntity(t: T): Entity = {
    val key = Key()
      .withPartitionId(
        PartitionId(
          projectId = projectId,
          databaseId = databaseId,
          namespaceId = namespaceId
        )
      )
      .addPath(PathElement(kind = kind, idType = idType(t)))
    encode(t).valueType.entityValue.get.withKey(key)
  }

  def withId(f: T => Long) = this.copy(idType = f.andThen(IdType.Id))
  def withName(f: T => String) = this.copy(idType = f.andThen(IdType.Name))

  def excludeFromIndexes(selectors: T => Any*): Encoder[T] =
    macro Encoder.excludeFromIndexesImpl[T]

  def contramap[A](f: A => T) = Encoder[A](
    base.compose(f),
    kind,
    idType.compose(f),
    projectId,
    databaseId,
    namespaceId,
    excludedFields
  )
}

object Encoder {
  type Typeclass[T] = Encoder[T]

  def join[T](ctx: CaseClass[Encoder, T]): Encoder[T] = Encoder[T](
    base = { t =>
      val properties = ctx.parameters.map { p =>
        p.label -> p.typeclass.encode(p.dereference(t))
      }.toMap
      Value().withEntityValue(Entity(properties = properties))
    },
    kind = ctx.typeName.short
  )

  implicit def gen[T]: Encoder[T] = macro Magnolia.gen[T]

  implicit val boolean: Encoder[Boolean] = Encoder(Value().withBooleanValue)
  implicit val int: Encoder[Int] =
    Encoder(t => Value().withIntegerValue(t.toLong))
  implicit val long: Encoder[Long] = Encoder(Value().withIntegerValue)
  implicit val double: Encoder[Double] = Encoder(Value().withDoubleValue)
  implicit val string: Encoder[String] = Encoder(Value().withStringValue)
  implicit val timestamp: Encoder[Instant] = Encoder(t =>
    Value().withTimestampValue(
      Timestamp(
        seconds = t.getEpochSecond(),
        nanos = ((t.toEpochMilli() % 1000) * 1000000).toInt
      )
    )
  )
  implicit val key: Encoder[Key] = Encoder(Value().withKeyValue)
  implicit val blob: Encoder[Array[Byte]] =
    Encoder(t => Value().withBlobValue(ByteString.copyFrom(t)))
  implicit val geoPoint: Encoder[LatLng] = Encoder(Value().withGeoPointValue)
  implicit def option[T: Encoder]: Encoder[Option[T]] = Encoder(
    base = {
      case Some(t) => implicitly[Encoder[T]].encode(t)
      case None    => Value().withNullValue(NullValue.NULL_VALUE)
    }
  )
  implicit def seq[T: Encoder]: Encoder[Seq[T]] = Encoder(seq =>
    Value().withArrayValue(ArrayValue(seq.map(implicitly[Encoder[T]].encode)))
  )

  def excludeFromIndexesImpl[T](c: Context)(selectors: c.Expr[T => Any]*) = {
    import c.universe._

    val names = selectors.map(_.tree).map {
      case q"_.$name" => name.toString()
      case _          => throw new Exception("Only _.field pattern is supported")
    }

    q"""${c.prefix}.copy(excludedFields = Set(..${names}))"""
  }
}
