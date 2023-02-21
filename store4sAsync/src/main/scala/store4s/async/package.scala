package store4s

import store4s.async.model.CompositeFilter
import store4s.async.model.Entity
import store4s.async.model.Filter
import store4s.async.model.Key
import store4s.async.model.PartitionId
import store4s.async.model.PathElement

import scala.language.implicitConversions
import scala.reflect.runtime.universe._

package object async {
  implicit class EntityEncoderOps[A: WeakTypeTag: EntityEncoder, F[_]](obj: A) {
    val kind = weakTypeOf[A].typeSymbol.name.toString()

    def asEntity(id: Long, namespace: String)(implicit ds: Datastore[F, _]) = {
      EntityEncoder[A].encode(
        obj,
        Some(
          Key(
            PartitionId(ds.projectId, Option(namespace)),
            Seq(PathElement(kind, Some(id.toString), None))
          )
        ),
        Set.empty[String]
      )
    }

    def asEntity(name: String, namespace: String)(implicit
        ds: Datastore[F, _]
    ) = {
      EntityEncoder[A].encode(
        obj,
        Some(
          Key(
            PartitionId(ds.projectId, Option(namespace)),
            Seq(PathElement(kind, None, Some(name)))
          )
        ),
        Set.empty[String]
      )
    }

    def asEntity(id: Long)(implicit ds: Datastore[F, _]): Entity =
      asEntity(id, null)

    def asEntity(name: String)(implicit ds: Datastore[F, _]): Entity =
      asEntity(name, null)
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
