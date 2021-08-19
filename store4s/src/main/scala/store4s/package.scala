import com.google.cloud.datastore.Entity
import com.google.cloud.datastore.EntityQuery
import com.google.cloud.datastore.FullEntity
import com.google.cloud.datastore.KeyFactory
import com.google.cloud.datastore.StructuredQuery.CompositeFilter
import com.google.cloud.datastore.StructuredQuery.Filter
import scala.language.implicitConversions
import scala.reflect.runtime.universe._

package object store4s {
  implicit class EntityEncoderOps[A: WeakTypeTag](obj: A) {
    def asEntity(implicit encoder: EntityEncoder[A], ds: Datastore) = encoder
      .encodeEntity(obj, FullEntity.newBuilder(ds.keyFactory[A].newKey()))
      .build()

    def asEntity(name: String)(implicit
        encoder: EntityEncoder[A],
        ds: Datastore
    ) = encoder
      .encodeEntity(obj, Entity.newBuilder(ds.keyFactory[A].newKey(name)))
      .build()

    def asEntity(id: Long)(implicit
        encoder: EntityEncoder[A],
        ds: Datastore
    ) = encoder
      .encodeEntity(obj, Entity.newBuilder(ds.keyFactory[A].newKey(id)))
      .build()
  }

  implicit class FilterWrapper(left: Filter) {
    def &&(right: Filter): Filter = CompositeFilter.and(left, right)
  }

  implicit class BooleanPropertyWrapper(p: Query.Property[Boolean]) {
    def unary_! = p == false
  }

  implicit def booleanProperty2Filter(p: Query.Property[Boolean]): Filter = {
    p == true
  }
}