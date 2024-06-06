package store4s

import com.google.datastore.v1.entity.Entity
import com.google.datastore.v1.query.CompositeFilter
import com.google.datastore.v1.query.CompositeFilter.Operator.AND
import com.google.datastore.v1.query.Filter

package object rpc {
  implicit class EncoderOps[T](t: T)(implicit enc: Encoder[T]) {
    def asEntity(id: Long): Entity = enc.withId(_ => id).encodeEntity(t)
    def asEntity(name: String): Entity = enc.withName(_ => name).encodeEntity(t)
    def asEntity: Entity = enc.encodeEntity(t)
  }

  implicit class EntityOps(entity: Entity) {
    def as[T: Decoder]: T = implicitly[Decoder[T]].decodeEntity(entity)
  }

  implicit class FilterWrapper(left: Filter) {
    def &&(right: Filter) = Filter().withCompositeFilter(
      CompositeFilter(op = AND).addFilters(left, right)
    )
  }

  implicit class BooleanPropertyWrapper(p: Query.Property[Boolean]) {
    def unary_! = p == false
  }

  implicit def booleanProperty2Filter(p: Query.Property[Boolean]): Filter = {
    p == true
  }
}
