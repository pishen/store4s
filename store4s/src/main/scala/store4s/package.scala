import com.google.cloud.datastore.Entity
import com.google.cloud.datastore.EntityQuery
import com.google.cloud.datastore.FullEntity
import com.google.cloud.datastore.KeyFactory
import com.google.cloud.datastore.StructuredQuery.CompositeFilter
import com.google.cloud.datastore.StructuredQuery.Filter
import scala.language.implicitConversions
import scala.reflect.runtime.universe._

package object store4s {
  implicit class QueryBuilderWrapper(eb: EntityQuery.Builder) {
    def applyIf(
        cond: Boolean
    )(f: EntityQuery.Builder => EntityQuery.Builder) = {
      if (cond) f(eb) else eb
    }
  }

  implicit class EntityEncoderOps[A: WeakTypeTag](obj: A)(implicit
      encoder: EntityEncoder[A],
      datastore: Datastore
  ) {
    val keyFactory = datastore.keyFactory[A]

    def asEntity = encoder
      .encodeEntity(obj, FullEntity.newBuilder(keyFactory.newKey()))
      .build()

    def asEntity(name: String) = encoder
      .encodeEntity(obj, Entity.newBuilder(keyFactory.newKey(name)))
      .build()

    def asEntity(id: Long) = encoder
      .encodeEntity(obj, Entity.newBuilder(keyFactory.newKey(id)))
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
