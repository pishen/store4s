package scalastore

import com.google.cloud.datastore.BooleanValue
import com.google.cloud.datastore.EntityValue
import com.google.cloud.datastore.FullEntity
import com.google.cloud.datastore.IncompleteKey
import com.google.cloud.datastore.LongValue
import com.google.cloud.datastore.StringValue
import com.google.cloud.datastore.Value
import magnolia._
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

  implicit val stringEncoder = create(StringValue.of)
  implicit val longEncoder = create(LongValue.of)
  implicit val intEncoder = create((i: Int) => LongValue.of(i.toLong))
  implicit val booleanEncoder = create(BooleanValue.of)
}

trait EntityEncoder[T] extends ValueEncoder[T] {
  def encodeEntity(t: T): FullEntity[IncompleteKey]
}

object EntityEncoder {
  type Typeclass[T] = ValueEncoder[T]

  def combine[T](ctx: CaseClass[ValueEncoder, T]): EntityEncoder[T] =
    new EntityEncoder[T] {
      type Out = FullEntity[_]

      def encodeEntity(t: T) = {
        val eb = ctx.parameters.foldLeft(FullEntity.newBuilder()) { (eb, p) =>
          eb.set(
            p.label,
            p.typeclass.encode(p.dereference(t))
          )
        }
        eb.build()
      }

      def encode(t: T) = {
        EntityValue.of(encodeEntity(t))
      }
    }

  implicit def gen[T]: EntityEncoder[T] = macro Magnolia.gen[T]
}
