package store4s

import store4s.async.model.CompositeFilter
import store4s.async.model.Filter
import store4s.async.model.Key
import store4s.async.model.PartitionId
import store4s.async.model.PathElement
import scala.language.implicitConversions

import scala.reflect.runtime.universe._

package object async {
  implicit class EntityEncoderOps[A: WeakTypeTag](obj: A) {
    val kind = weakTypeOf[A].typeSymbol.name.toString()

    def asEntity(implicit partitionId: PartitionId, enc: EntityEncoder[A]) =
      enc.encode(
        obj,
        Some(Key(partitionId, Seq(PathElement(kind, None, None)))),
        Set.empty[String]
      )

    def asEntity(id: Long)(implicit
        partitionId: PartitionId,
        enc: EntityEncoder[A]
    ) = enc.encode(
      obj,
      Some(Key(partitionId, Seq(PathElement(kind, Some(id.toString), None)))),
      Set.empty[String]
    )

    def asEntity(name: String)(implicit
        partitionId: PartitionId,
        enc: EntityEncoder[A]
    ) = enc.encode(
      obj,
      Some(Key(partitionId, Seq(PathElement(kind, None, Some(name))))),
      Set.empty[String]
    )
  }

  implicit class FilterWrapper(left: Filter) {
    def &&(right: Filter) =
      Filter(compositeFilter = Some(CompositeFilter("AND", Seq(left, right))))
  }

  implicit class BooleanPropertyWrapper(p: Query.Property[Boolean]) {
    def unary_! = p == false
  }

  implicit def booleanProperty2Filter(p: Query.Property[Boolean]): Filter = {
    p == true
  }
}
